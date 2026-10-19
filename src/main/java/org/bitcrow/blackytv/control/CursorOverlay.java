package org.bitcrow.blackytv.control;

public final class CursorOverlay {

    private static final String[] ARROW = {
            "X          ",
            "XX         ",
            "X.X        ",
            "X..X       ",
            "X...X      ",
            "X....X     ",
            "X.....X    ",
            "X......X   ",
            "X.......X  ",
            "X........X ",
            "X.....XXXXX",
            "X..X..X    ",
            "X.X X..X   ",
            "XX  X..X   ",
            "X    X..X  ",
            "     XXXX  ",
    };
    private static final int OUTLINE = 0xFF000000;
    private static final int FILL = 0xFFFFFFFF;
    private static final int REFERENCE_WIDTH = 640;

    private CursorOverlay() {
    }

    public static void draw(int[] argb, int width, int height, double u, double v) {
        int scale = Math.max(1, width / REFERENCE_WIDTH);
        int originX = (int) Math.floor(u * width), originY = (int) Math.floor(v * height);
        for (int row = 0; row < ARROW.length; row++) {
            String line = ARROW[row];
            for (int col = 0; col < line.length(); col++) {
                char c = line.charAt(col);
                if (c == ' ') continue;
                int color = c == 'X' ? OUTLINE : FILL;
                for (int dy = 0; dy < scale; dy++) {
                    int y = originY + row * scale + dy;
                    if (y < 0 || y >= height) continue;
                    for (int dx = 0; dx < scale; dx++) {
                        int x = originX + col * scale + dx;
                        if (x >= 0 && x < width) argb[y * width + x] = color;
                    }
                }
            }
        }
    }
}
