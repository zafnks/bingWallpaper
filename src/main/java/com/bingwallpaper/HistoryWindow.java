package com.bingwallpaper;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Window displaying all downloaded wallpapers as thumbnails.
 * Click a thumbnail to select it, then click "切换" to apply.
 */
public class HistoryWindow extends JFrame {
    private static final Logger LOG = Logger.getLogger(HistoryWindow.class.getName());

    private List<String> paths;
    private final Consumer<Integer> onSelect;
    private int selectedIndex = -1;
    private int currentIndex;
    private JButton switchBtn;
    private JPanel grid;

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

        // ── Top: switch button + hint ──
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        switchBtn = new JButton("切换");
        switchBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        switchBtn.setEnabled(false);
        switchBtn.addActionListener(e -> {
            if (selectedIndex >= 0) {
                onSelect.accept(selectedIndex);
                // Update window to show the new current wallpaper
                HistoryWindow.this.currentIndex = selectedIndex;
                rebuildGrid();
            }
        });
        topBar.add(switchBtn);

        JLabel hint = new JLabel("点击缩略图选中，再点 [切换] 按钮应用");
        hint.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        hint.setForeground(Color.GRAY);
        topBar.add(hint);
        add(topBar, BorderLayout.NORTH);

        // ── Thumbnail grid ──
        grid = new JPanel(new GridLayout(0, 5, 10, 10));
        grid.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        rebuildGrid();
        JScrollPane scroll = new JScrollPane(grid);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    /** Called when the window is still showing but history has changed. */
    public void refresh(List<String> newPaths, int newCurrentIndex) {
        this.paths = newPaths;
        this.currentIndex = newCurrentIndex;
        this.selectedIndex = -1;
        switchBtn.setEnabled(false);
        grid.removeAll();
        rebuildGrid();
        grid.revalidate();
        grid.repaint();
    }

    /** Rebuild the thumbnail grid for the current paths and currentIndex. */
    private void rebuildGrid() {
        grid.removeAll();
        for (int i = paths.size() - 1; i >= 0; i--) {
            int idx = i;
            File file = new File(paths.get(i));
            if (!file.exists()) continue;

            JPanel card = new JPanel(new BorderLayout(0, 4));
            boolean isCurrent = (i == currentIndex);
            card.setBorder(BorderFactory.createLineBorder(
                    isCurrent ? new Color(0, 120, 215) : Color.LIGHT_GRAY,
                    isCurrent ? 3 : 2
            ));
            card.setPreferredSize(new Dimension(200, 130));

            // Thumbnail
            JLabel thumb = createThumbnail(file);
            thumb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            thumb.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    selectCard(idx);
                }
            });
            card.add(thumb, BorderLayout.CENTER);

            // Date label
            String date = file.getName()
                    .replaceAll("bing_wallpaper_|\\.jpg", "")
                    .replace("_", " ");
            JLabel dateLabel = new JLabel(date, SwingConstants.CENTER);
            dateLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
            dateLabel.setForeground(Color.GRAY);
            card.add(dateLabel, BorderLayout.SOUTH);

            grid.add(card);
        }

        // If current index points to a valid entry, pre-select it
        if (currentIndex >= 0 && currentIndex < paths.size()) {
            File f = new File(paths.get(currentIndex));
            if (f.exists()) {
                selectCard(currentIndex);
            }
        }
    }

    /** Highlight the card at the given history index and enable the switch button. */
    private void selectCard(int idx) {
        selectedIndex = idx;
        // Reset all borders, then highlight selected
        for (Component c : grid.getComponents()) {
            if (c instanceof JPanel) {
                ((JPanel) c).setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 2));
            }
        }
        int slot = 0;
        for (int i = paths.size() - 1; i >= 0 && slot < grid.getComponentCount(); i--) {
            if (!new File(paths.get(i)).exists()) continue;
            if (i == idx) {
                Component card = grid.getComponent(slot);
                if (card instanceof JPanel) {
                    ((JPanel) card).setBorder(BorderFactory.createLineBorder(new Color(0, 120, 215), 3));
                }
                break;
            }
            slot++;
        }
        switchBtn.setEnabled(true);
    }

    private static final int THUMB_WIDTH = 200;

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
}
