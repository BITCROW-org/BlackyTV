package org.bitcrow.blackytv.capture;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import org.bitcrow.blackytv.stream.StreamSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class AudioCapture {

    private static final Logger LOG = LoggerFactory.getLogger("BlackyTV");

    private static final int LOOPBACK_INCLUDE_TREE = 0;
    private static final int LOOPBACK_EXCLUDE_TREE = 1;

    private static final Memory IID_UNKNOWN = guid("00000000-0000-0000-C000-000000000046");
    private static final Memory IID_AGILE = guid("94ea2b94-e9cc-49e0-c0ff-ee64ca8f5b90");
    private static final Memory IID_HANDLER = guid("41D949AB-9862-444A-80F6-C261334DA5EB");
    private static final Memory IID_AUDIO_CLIENT = guid("1CB9AD4C-DBFA-4c32-B178-C2F568A703B2");
    private static final Memory IID_CAPTURE_CLIENT = guid("C8ADBD64-E71E-48a0-A4DE-185C395CD317");

    public interface QueryInterface extends Callback {
        int invoke(Pointer self, Pointer riid, Pointer ppv);
    }

    public interface AddRefRelease extends Callback {
        int invoke(Pointer self);
    }

    public interface ActivateCompleted extends Callback {
        int invoke(Pointer self, Pointer operation);
    }

    private static volatile CountDownLatch activated;

    private static final QueryInterface QUERY_INTERFACE = (self, riid, ppv) -> {
        byte[] id = riid.getByteArray(0, 16);
        for (Memory known : new Memory[]{IID_UNKNOWN, IID_AGILE, IID_HANDLER}) {
            if (Arrays.equals(id, known.getByteArray(0, 16))) {
                ppv.setPointer(0, self);
                return 0;
            }
        }
        ppv.setPointer(0, null);
        return 0x80004002;
    };
    private static final AddRefRelease REF_COUNT = self -> 1;
    private static final ActivateCompleted ON_ACTIVATED = (self, operation) -> {
        activated.countDown();
        return 0;
    };
    private static final Memory VTABLE = new Memory(4L * Native.POINTER_SIZE);
    private static final Memory HANDLER = new Memory(Native.POINTER_SIZE);

    static {
        Callback[] methods = {QUERY_INTERFACE, REF_COUNT, REF_COUNT, ON_ACTIVATED};
        for (int i = 0; i < methods.length; i++)
            VTABLE.setPointer((long) i * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(methods[i]));
        HANDLER.setPointer(0, VTABLE);
    }

    private AudioCapture() {
    }

    public static void start() {
        Thread thread = new Thread(AudioCapture::run, "BlackyTV-Audio");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        Win32.function("ole32", "CoInitializeEx").invokeInt(new Object[]{null, 0});
        while (true) {
            CaptureSource s = ScreenCapture.source();
            if (StreamSender.isSending() && s != null) {
                try {
                    long pid = s.isWindow() ? windowProcess(s.hwnd()) : ProcessHandle.current().pid();
                    int mode = s.isWindow() ? LOOPBACK_INCLUDE_TREE : LOOPBACK_EXCLUDE_TREE;
                    if (pid > 0)
                        capture(pid, mode, () -> StreamSender.isSending() && ScreenCapture.source() == s, StreamSender::pushAudio);
                } catch (Throwable e) {
                    LOG.warn("Audio capture failed: {}", e.getMessage());
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static long windowProcess(Pointer hwnd) {
        IntByReference pid = new IntByReference();
        Win32.user32("GetWindowThreadProcessId", hwnd, pid);
        return pid.getValue();
    }

    private static void capture(long pid, int mode, BooleanSupplier keepGoing, Consumer<byte[]> sink) throws InterruptedException {
        Memory params = new Memory(12);
        params.setInt(0, 1);
        params.setInt(4, (int) pid);
        params.setInt(8, mode);
        Memory variant = new Memory(24);
        variant.clear();
        variant.setShort(0, (short) 65);
        variant.setInt(8, 12);
        variant.setPointer(16, params);

        activated = new CountDownLatch(1);
        PointerByReference operation = new PointerByReference();
        check(Win32.function("Mmdevapi", "ActivateAudioInterfaceAsync").invokeInt(new Object[]{
                new WString("VAD\\Process_Loopback"), IID_AUDIO_CLIENT, variant, HANDLER, operation}), "ActivateAudioInterfaceAsync");
        if (!activated.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Audio activation timed out");

        IntByReference result = new IntByReference();
        PointerByReference unknown = new PointerByReference();
        com(operation.getValue(), 3, result, unknown);
        com(operation.getValue(), 2);
        check(result.getValue(), "GetActivateResult");
        PointerByReference ref = new PointerByReference();
        check(com(unknown.getValue(), 0, IID_AUDIO_CLIENT, ref), "QueryInterface");
        com(unknown.getValue(), 2);
        Pointer client = ref.getValue();

        Memory format = new Memory(18);
        format.setShort(0, (short) 1);
        format.setShort(2, (short) 2);
        format.setInt(4, Pcm.SAMPLE_RATE);
        format.setInt(8, Pcm.SAMPLE_RATE * 4);
        format.setShort(12, (short) 4);
        format.setShort(14, (short) 16);
        format.setShort(16, (short) 0);

        Pointer event = Win32.function("kernel32", "CreateEventW").invokePointer(new Object[]{null, 0, 0, null});
        Pointer captureClient = null;
        try {
            check(com(client, 3, 0, 0x80060000, 2_000_000L, 0L, format, null), "Initialize");
            check(com(client, 13, event), "SetEventHandle");
            PointerByReference cc = new PointerByReference();
            check(com(client, 14, IID_CAPTURE_CLIENT, cc), "GetService");
            captureClient = cc.getValue();
            check(com(client, 10), "Start");

            byte[] chunk = new byte[Pcm.FRAME_BYTES];
            int filled = 0;
            IntByReference packet = new IntByReference(), frames = new IntByReference(), flags = new IntByReference();
            PointerByReference data = new PointerByReference();
            while (keepGoing.getAsBoolean() && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                Win32.function("kernel32", "WaitForSingleObject").invokeInt(new Object[]{event, 200});
                while (com(captureClient, 5, packet) == 0 && packet.getValue() > 0) {
                    check(com(captureClient, 3, data, frames, flags, null, null), "GetBuffer");
                    int n = frames.getValue() * 4;
                    byte[] buffer = (flags.getValue() & 2) != 0 ? new byte[n] : data.getValue().getByteArray(0, n);
                    com(captureClient, 4, frames.getValue());
                    for (int offset = 0; offset < n; ) {
                        int count = Math.min(n - offset, Pcm.FRAME_BYTES - filled);
                        System.arraycopy(buffer, offset, chunk, filled, count);
                        offset += count;
                        filled += count;
                        if (filled == Pcm.FRAME_BYTES) {
                            sink.accept(chunk.clone());
                            filled = 0;
                        }
                    }
                }
            }
        } finally {
            com(client, 11);
            if (captureClient != null) com(captureClient, 2);
            com(client, 2);
            Win32.function("kernel32", "CloseHandle").invokeInt(new Object[]{event});
        }
    }

    private static int com(Pointer object, int index, Object... args) {
        Pointer method = object.getPointer(0).getPointer((long) index * Native.POINTER_SIZE);
        Object[] all = new Object[args.length + 1];
        all[0] = object;
        System.arraycopy(args, 0, all, 1, args.length);
        return Function.getFunction(method).invokeInt(all);
    }

    private static void check(int hresult, String what) {
        if (hresult < 0) throw new IllegalStateException(String.format("%s failed: 0x%08X", what, hresult));
    }

    private static Memory guid(String text) {
        UUID uuid = UUID.fromString(text);
        long hi = uuid.getMostSignificantBits(), lo = uuid.getLeastSignificantBits();
        Memory m = new Memory(16);
        m.setInt(0, (int) (hi >>> 32));
        m.setShort(4, (short) (hi >>> 16));
        m.setShort(6, (short) hi);
        for (int i = 0; i < 8; i++) m.setByte(8 + i, (byte) (lo >>> (56 - 8 * i)));
        return m;
    }
}
