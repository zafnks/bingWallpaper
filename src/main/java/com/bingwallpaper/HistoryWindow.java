package com.bingwallpaper;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Window displaying all downloaded wallpapers as thumbnails.
 * Thumbnails load asynchronously; a spinning indicator is shown while loading.
 */
public class HistoryWindow extends JFrame {
    private static final Logger LOG = Logger.getLogger(HistoryWindow.class.getName());
    private static final int THUMB_WIDTH = 200;

    private List<String> paths;
    private final Consumer<Integer> onSelect;
    private int selectedIndex = -1;
    private int currentIndex;
    private JButton switchBtn;
    private JPanel grid;
    private JScrollPane scroll;
    private LoadingSpinner spinner;
    private SwingWorker<List<CardData>, Void> loader;

    /** Card built on the worker thread, for final assembly on the EDT. */
    private static class CardData {
        final int pathIndex;
        final JPanel card;
        CardData(int pathIndex, JPanel card) { this.pathIndex = pathIndex; this.card = card; }
    }

    public HistoryWindow(List<String> paths, int currentIndex, Consumer<Integer> onSelect) {
        super("历史壁纸");
        this.paths = paths;
        this.onSelect = onSelect;
        this.currentIndex = currentIndex;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1100, 600);
        setLocationRelativeTo(null);
        setIconImage(TrayManager.createTrayIconImage());
        setLayout(new BorderLayout());

        // ── Top bar ──
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        switchBtn = new JButton("切换");
        switchBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        switchBtn.setEnabled(false);
        switchBtn.addActionListener(e -> {
            if (selectedIndex >= 0) {
                onSelect.accept(selectedIndex);
                HistoryWindow.this.currentIndex = selectedIndex;
                highlightCurrent();
            }
        });
        topBar.add(switchBtn);

        JLabel hint = new JLabel("点击缩略图选中，再点 [切换] 按钮应用");
        hint.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        hint.setForeground(Color.GRAY);
        topBar.add(hint);
        add(topBar, BorderLayout.NORTH);

        // ── Grid area ──
        grid = new JPanel(new GridLayout(0, 5, 10, 10));
        grid.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        spinner = new LoadingSpinner();

        scroll = new JScrollPane();
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setViewportView(wrapCentered(spinner));
        add(scroll, BorderLayout.CENTER);

        // Start async load
        startLoading();
    }

    /** Called when the window is already open but history has changed. */
    public void refresh(List<String> newPaths, int newCurrentIndex) {
        this.paths = newPaths;
        this.currentIndex = newCurrentIndex;
        this.selectedIndex = -1;
        switchBtn.setEnabled(false);
        cancelLoader();
        scroll.setViewportView(wrapCentered(spinner));
        spinner.start();
        startLoading();
    }

    /** Center a component inside a panel that fills the scroll-pane viewport. */
    private static JPanel wrapCentered(JComponent child) {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.add(child);
        return wrapper;
    }

    private void cancelLoader() {
        if (loader != null && !loader.isDone()) {
            loader.cancel(true);
        }
    }

    private void startLoading() {
        cancelLoader();
        spinner.start();

        final List<String> snapshot = new ArrayList<>(paths);
        final int currentAtStart = currentIndex;

        loader = new SwingWorker<List<CardData>, Void>() {
            @Override
            protected List<CardData> doInBackground() {
                List<CardData> cards = new ArrayList<>();
                for (int i = snapshot.size() - 1; i >= 0; i--) {
                    if (isCancelled()) return cards;
                    File file = new File(snapshot.get(i));
                    if (!file.exists()) continue;

                    JLabel thumb = createThumbnail(file);
                    thumb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    final int idx = i;
                    thumb.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            selectCard(idx);
                        }
                    });

                    JPanel card = new JPanel(new BorderLayout(0, 4));
                    card.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 2));
                    card.setPreferredSize(new Dimension(200, 130));
                    card.add(thumb, BorderLayout.CENTER);

                    String date = file.getName()
                            .replaceAll("bing_wallpaper_|\\.jpg", "")
                            .replace("_", " ");
                    JLabel dateLabel = new JLabel(date, SwingConstants.CENTER);
                    dateLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
                    dateLabel.setForeground(Color.GRAY);
                    card.add(dateLabel, BorderLayout.SOUTH);

                    cards.add(new CardData(i, card));
                }
                return cards;
            }

            @Override
            protected void done() {
                spinner.stop();
                grid.removeAll();

                List<CardData> cards;
                try {
                    cards = get();
                } catch (Exception e) {
                    LOG.warning("Thumbnail loading failed: " + e.getMessage());
                    grid.add(new JLabel("加载失败", SwingConstants.CENTER));
                    scroll.setViewportView(grid);
                    return;
                }

                for (CardData cd : cards) {
                    boolean isCurrent = (cd.pathIndex == currentAtStart);
                    if (isCurrent) {
                        cd.card.setBorder(BorderFactory.createLineBorder(new Color(0, 120, 215), 3));
                    }
                    grid.add(cd.card);
                }

                // Pre-select current
                if (currentAtStart >= 0 && currentAtStart < snapshot.size()) {
                    File f = new File(snapshot.get(currentAtStart));
                    if (f.exists()) {
                        selectCardInternal(currentAtStart, snapshot);
                    }
                }

                scroll.setViewportView(grid);
                loader = null;
            }
        };
        loader.execute();
    }

    private void highlightCurrent() {
        for (Component c : grid.getComponents()) {
            if (c instanceof JPanel) {
                ((JPanel) c).setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 2));
            }
        }
        // The currentIndex was already updated; find the card at that slot
        int slot = 0;
        for (int i = paths.size() - 1; i >= 0; i--) {
            if (!new File(paths.get(i)).exists()) continue;
            if (i == currentIndex && slot < grid.getComponentCount()) {
                Component c = grid.getComponent(slot);
                if (c instanceof JPanel) {
                    ((JPanel) c).setBorder(BorderFactory.createLineBorder(new Color(0, 120, 215), 3));
                }
                break;
            }
            slot++;
        }
    }

    /** Highlight a card by path index (called from mouse listener). */
    private void selectCard(int idx) {
        selectCardInternal(idx, paths);
        switchBtn.setEnabled(true);
    }

    private void selectCardInternal(int idx, List<String> pathList) {
        selectedIndex = idx;
        int slot = 0;
        for (int i = pathList.size() - 1; i >= 0 && slot < grid.getComponentCount(); i--) {
            if (!new File(pathList.get(i)).exists()) continue;
            Component c = grid.getComponent(slot);
            if (c instanceof JPanel) {
                ((JPanel) c).setBorder(i == idx
                        ? BorderFactory.createLineBorder(new Color(0, 120, 215), 3)
                        : BorderFactory.createLineBorder(Color.LIGHT_GRAY, 2));
            }
            slot++;
        }
    }

    // ── Thumbnail loading ─────────────────────────────────────────────

    /** Load a JPEG at reduced resolution and return a 200px-wide thumbnail label. */
    private static JLabel createThumbnail(File file) {
        try {
            BufferedImage src = readThumbnail(file);
            if (src == null) return new JLabel("(加载失败)");
            int h = THUMB_WIDTH * src.getHeight() / src.getWidth();
            BufferedImage scaled = new BufferedImage(THUMB_WIDTH, Math.max(h, 1), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = scaled.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(src, 0, 0, THUMB_WIDTH, h, null);
            g2.dispose();
            return new JLabel(new ImageIcon(scaled));
        } catch (Exception e) {
            LOG.warning("Failed to load thumbnail: " + file.getName());
            return new JLabel("(加载失败)");
        }
    }

    /** Read a JPEG at reduced resolution using subsampling, falling back to full decode for other formats. */
    private static BufferedImage readThumbnail(File file) throws Exception {
        String name = file.getName().toLowerCase();
        if (!name.endsWith(".jpg") && !name.endsWith(".jpeg")) {
            return ImageIO.read(file);
        }

        ImageInputStream iis = ImageIO.createImageInputStream(file);
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("jpeg");
        if (!readers.hasNext()) {
            iis.close();
            return ImageIO.read(file);
        }

        ImageReader reader = readers.next();
        try {
            reader.setInput(iis);
            int srcW = reader.getWidth(0);
            int srcH = reader.getHeight(0);
            int subsample = Math.max(1, Math.max(srcW, srcH) / THUMB_WIDTH);

            ImageReadParam param = reader.getDefaultReadParam();
            param.setSourceSubsampling(subsample, subsample, 0, 0);
            return reader.read(0, param);
        } finally {
            reader.dispose();
            iis.close();
        }
    }

    // ── Loading spinner ───────────────────────────────────────────────

    /** Animated spinning arc with elapsed-time-based angle (no drift / jitter). */
    private static class LoadingSpinner extends JPanel {
        private static final int SIZE = 56;
        private static final double FULL_TURN_SEC = 1.0;
        private static final Color ARC_COLOR = new Color(0, 120, 215);
        private static final BasicStroke STROKE = new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

        private final Timer timer;
        private long startNanos;

        LoadingSpinner() {
            setPreferredSize(new Dimension(220, 160));
            setOpaque(false);
            startNanos = 0;
            timer = new Timer(16, e -> repaint());
        }

        void start() {
            startNanos = System.nanoTime();
            timer.start();
        }

        void stop() { timer.stop(); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;

            // Angle derived from continuous elapsed time — immune to timer drift
            double elapsed = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            double angle = (elapsed / FULL_TURN_SEC * 2.0 * Math.PI) % (2.0 * Math.PI);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(STROKE);
            g2.setColor(ARC_COLOR);

            int x = (w - SIZE) / 2;
            int y = (h - SIZE) / 2 - 14;
            g2.draw(new Arc2D.Double(x, y, SIZE, SIZE, Math.toDegrees(angle), 300, Arc2D.OPEN));

            g2.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            g2.setColor(Color.GRAY);
            FontMetrics fm = g2.getFontMetrics();
            String loadingText = "加载中...";
            g2.drawString(loadingText, (w - fm.stringWidth(loadingText)) / 2, y + SIZE + 28);

            g2.dispose();
        }
    }
}
