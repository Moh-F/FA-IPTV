import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Draws the FA IPTV logo at build time so the version number is always printed on the icon
 * (diagonal sash at the bottom corner). Pure Java2D, runs headless on CI and on any PC.
 */
public final class IconGen {
    static final Color BG1 = new Color(0x070B1F);
    static final Color BG2 = new Color(0x1A1450);
    static final Color CYAN = new Color(0x22D3EE);
    static final Color VIOLET = new Color(0x8B5CF6);
    static final Color PINK = new Color(0xEC4899);

    private IconGen() {}

    static Graphics2D g(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g;
    }

    /** The mark itself (TV frame, play triangle, signal arcs) inside a box of size s at (x,y). */
    static void mark(Graphics2D g, float x, float y, float s) {
        Paint grad = new LinearGradientPaint(x, y + s, x + s, y,
            new float[]{0f, 0.55f, 1f}, new Color[]{CYAN, VIOLET, PINK});
        // screen
        float sw = s * 0.07f;
        RoundRectangle2D screen = new RoundRectangle2D.Float(x + s * 0.12f, y + s * 0.24f, s * 0.76f, s * 0.54f, s * 0.16f, s * 0.16f);
        g.setPaint(new Color(255, 255, 255, 18));
        g.fill(screen);
        g.setPaint(grad);
        g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(screen);
        // stand
        g.draw(new Line2D.Float(x + s * 0.38f, y + s * 0.88f, x + s * 0.62f, y + s * 0.88f));
        // play triangle
        Path2D tri = new Path2D.Float();
        float cx = x + s * 0.515f, cy = y + s * 0.51f, r = s * 0.15f;
        tri.moveTo(cx - r * 0.75f, cy - r);
        tri.lineTo(cx + r * 1.05f, cy);
        tri.lineTo(cx - r * 0.75f, cy + r);
        tri.closePath();
        g.setStroke(new BasicStroke(s * 0.035f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.fill(tri);
        g.draw(tri);
        // signal arcs (top right)
        g.setStroke(new BasicStroke(s * 0.045f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float ax = x + s * 0.80f, ay = y + s * 0.16f;
        for (int i = 1; i <= 2; i++) {
            float rr = s * 0.07f * i + s * 0.02f;
            g.draw(new Arc2D.Float(ax - rr, ay - rr, rr * 2, rr * 2, 100, 80, Arc2D.OPEN));
        }
        g.fill(new Ellipse2D.Float(ax - s * 0.03f, ay - s * 0.03f, s * 0.06f, s * 0.06f));
    }

    static void background(Graphics2D g, int w, int h, float radius) {
        Shape clip = new RoundRectangle2D.Float(0, 0, w, h, radius, radius);
        g.setClip(clip);
        g.setPaint(new GradientPaint(0, 0, BG1, w, h, BG2));
        g.fillRect(0, 0, w, h);
        // soft glow
        float gr = Math.max(w, h) * 0.75f;
        g.setPaint(new RadialGradientPaint(w * 0.5f, h * 0.45f, gr,
            new float[]{0f, 1f}, new Color[]{new Color(139, 92, 246, 90), new Color(139, 92, 246, 0)}));
        g.fillRect(0, 0, w, h);
    }

    /** Diagonal sash across the bottom-right corner carrying the version text. */
    static void sash(Graphics2D g, int w, int h, String text) {
        float s = Math.min(w, h);
        AffineTransform old = g.getTransform();
        g.translate(w, h);
        g.rotate(-Math.PI / 4);
        float band = s * 0.17f;
        float mid = -s * 0.25f;
        g.setPaint(new GradientPaint(-s, 0, new Color(0xF59E0B), s, 0, new Color(0xF97316)));
        g.fill(new Rectangle2D.Float(-s, mid - band / 2, s * 2, band));
        g.setPaint(new Color(0, 0, 0, 60));
        g.fill(new Rectangle2D.Float(-s, mid + band / 2 - s * 0.012f, s * 2, s * 0.012f));
        Font f = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(6, Math.round(band * 0.62f)));
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(text);
        g.setColor(new Color(0x111827));
        g.drawString(text, -tw / 2f, mid + (fm.getAscent() - fm.getDescent()) / 2f);
        g.setTransform(old);
    }

    public static BufferedImage icon(int size, String version, boolean withSash) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        background(g, size, size, size * 0.42f);
        mark(g, size * 0.13f, size * 0.10f, size * 0.74f);
        // "FA" wordmark under the mark (only where it is readable)
        if (size >= 96) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size * 0.12f)));
            g.setColor(new Color(255, 255, 255, 230));
            FontMetrics fm = g.getFontMetrics();
            String t = "FA";
            g.drawString(t, size * 0.13f + size * 0.02f, size * 0.25f);
        }
        if (withSash) sash(g, size, size, "v" + version);
        g.dispose();
        return img;
    }

    public static BufferedImage banner(int w, int h, String version) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        background(g, w, h, 0);
        float ms = h * 0.70f;
        mark(g, h * 0.12f, (h - ms) / 2f, ms);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.round(h * 0.26f)));
        float tx = h * 0.12f + ms + h * 0.06f;
        g.drawString("FA", tx, h * 0.47f);
        g.setPaint(new GradientPaint(tx, 0, CYAN, w, 0, VIOLET));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.round(h * 0.22f)));
        g.drawString("IPTV", tx, h * 0.74f);
        sash(g, w, h, "v" + version);
        g.dispose();
        return img;
    }

    static void write(BufferedImage img, File f) throws IOException {
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }

    /** Writes Android resource folders into [res]. */
    public static void generate(File res, String version) throws IOException {
        System.setProperty("java.awt.headless", "true");
        String[] dens = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        int[] px = {48, 72, 96, 144, 192};
        for (int i = 0; i < dens.length; i++) {
            write(icon(px[i], version, true), new File(res, "mipmap-" + dens[i] + "/ic_launcher.png"));
        }
        write(icon(512, version, true), new File(res, "drawable-nodpi/app_logo.png"));
        write(icon(512, version, false), new File(res, "drawable-nodpi/app_logo_plain.png"));
        write(banner(320, 180, version), new File(res, "drawable-mdpi/tv_banner.png"));
        write(banner(640, 360, version), new File(res, "drawable-xhdpi/tv_banner.png"));
    }

    public static void main(String[] a) throws IOException {
        generate(new File(a.length > 0 ? a[0] : "out"), a.length > 1 ? a[1] : "1.0.0");
    }
}
