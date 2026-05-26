package com.bingwallpaper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.logging.Logger;

/** System tray icon and right-click popup menu for the wallpaper app. */
public class TrayManager implements TrayCallback {
    private static final Logger LOG = Logger.getLogger(TrayManager.class.getName());

    /** All actions that the tray menu can trigger. */
    public interface MenuActions {
        void onPrevious();
        void onNext();
        void onUpdateNow();
        void onBrowseHistory();
        void onExit();
        void onScheduleChanged(int intervalHours, boolean dailyMode, int dailyHour);
        void onAutoStartToggled(boolean enabled);
        void onRetentionChanged(int days);
        void onResolutionChanged(String resolution);
    }

    private TrayIcon trayIcon;
    private JPopupMenu popupMenu;
    private JMenuItem prevItem;
    private JMenuItem nextItem;
    private JMenuItem autoStartItem;
    private boolean autoStartEnabled;
    private JRadioButtonMenuItem daily8;
    private JRadioButtonMenuItem daily20;
    private JRadioButtonMenuItem every6;
    private JRadioButtonMenuItem every12;
    private JRadioButtonMenuItem noSchedule;
    private JRadioButtonMenuItem retain3;
    private JRadioButtonMenuItem retain7;
    private JRadioButtonMenuItem retain30;
    private JRadioButtonMenuItem retain90;
    private JRadioButtonMenuItem retain180;
    private JRadioButtonMenuItem retain360;
    private JRadioButtonMenuItem retainNever;
    private JRadioButtonMenuItem res1080p;
    private JRadioButtonMenuItem res1200p;
    private JRadioButtonMenuItem res4k;
    private final MenuActions actions;

    public TrayManager(MenuActions actions) {
        this.actions = actions;
        createTrayIcon();
    }

    /** @return the TrayIcon added to the system tray. */
    public TrayIcon getTrayIcon() {
        return trayIcon;
    }

    @Override
    public void updateMenuState(boolean hasPrevious, boolean hasNext) {
        if (prevItem != null) prevItem.setEnabled(hasPrevious);
        if (nextItem != null) nextItem.setEnabled(hasNext);
    }

    @Override
    public void showError(String title, String message) {
        LOG.warning(title + ": " + message);
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.ERROR);
        }
    }

    /** Reflect current Settings state onto the menu items (after load or change). */
    public void applySettings(Settings s) {
        if (autoStartItem != null) {
            autoStartEnabled = s.isAutoStart();
            updateAutoStartText();
        }
        if (!s.isScheduledEnabled()) {
            selectRadio(noSchedule);
        } else if (s.isDailyMode()) {
            if (s.getDailyHour() == 8) selectRadio(daily8);
            else selectRadio(daily20);
        } else {
            if (s.getIntervalHours() == 6) selectRadio(every6);
            else selectRadio(every12);
        }
        switch (s.getRetentionDays()) {
            case 3:  selectRadio(retain3); break;
            case 7:  selectRadio(retain7); break;
            case 90: selectRadio(retain90); break;
            case 180: selectRadio(retain180); break;
            case 360: selectRadio(retain360); break;
            case 0:  selectRadio(retainNever); break;
            default: selectRadio(retain30); break;
        }
        // Resolution
        switch (s.getResolution()) {
            case "1080p": selectRadio(res1080p); break;
            case "1200p": selectRadio(res1200p); break;
            default:      selectRadio(res4k); break;
        }
    }

    private void selectRadio(JRadioButtonMenuItem item) {
        if (item != null) item.setSelected(true);
    }

    private void updateAutoStartText() {
        if (autoStartItem != null) {
            autoStartItem.setText(autoStartEnabled ? "取消开机自启" : "开机自启");
        }
    }

    // ── private helpers ────────────────────────────────────────────────

    private void createTrayIcon() {
        if (!SystemTray.isSupported()) {
            LOG.warning("System tray is not supported");
            return;
        }

        // Hidden dialog for JPopupMenu auto-dismiss
        final JDialog hiddenDialog = new JDialog();
        hiddenDialog.setType(Window.Type.UTILITY);
        hiddenDialog.setUndecorated(true);
        hiddenDialog.setSize(10, 10);
        hiddenDialog.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowLostFocus(WindowEvent e) { hiddenDialog.setVisible(false); }
            @Override
            public void windowGainedFocus(WindowEvent e) {}
        });

        Font font = new Font("Microsoft YaHei", Font.PLAIN, 19);
        popupMenu = new JPopupMenu();
        popupMenu.setBorder(BorderFactory.createEmptyBorder(5, 3, 5, 3));

        // ── Navigation ──
        prevItem = buildMenuItem("上一张", createIconPrev(), font, () -> {
            hiddenDialog.setVisible(false); actions.onPrevious();
        });
        popupMenu.add(prevItem);

        nextItem = buildMenuItem("下一张", createIconNext(), font, () -> {
            hiddenDialog.setVisible(false); actions.onNext();
        });
        popupMenu.add(nextItem);

        popupMenu.addSeparator();

        // ── Actions ──
        popupMenu.add(buildMenuItem("立即更新", createIconUpdate(), font, () -> {
            hiddenDialog.setVisible(false); actions.onUpdateNow();
        }));

        // ── Schedule submenu ──
        JMenu scheduleMenu = new JMenu("定时切换");
        scheduleMenu.setFont(font);
        scheduleMenu.setIcon(createIconClock());
        scheduleMenu.setBorder(BorderFactory.createEmptyBorder(3, 13, 3, 18));

        ButtonGroup scheduleGroup = new ButtonGroup();
        every6  = radioItem("每 6 小时", font, scheduleGroup, () -> { actions.onScheduleChanged(6, false, 0); });
        every12 = radioItem("每 12 小时", font, scheduleGroup, () -> { actions.onScheduleChanged(12, false, 0); });
        daily8  = radioItem("每天 8:00", font, scheduleGroup, () -> { actions.onScheduleChanged(24, true, 8); });
        daily20 = radioItem("每天 20:00", font, scheduleGroup, () -> { actions.onScheduleChanged(24, true, 20); });
        scheduleMenu.add(every6);
        scheduleMenu.add(every12);
        scheduleMenu.addSeparator();
        scheduleMenu.add(daily8);
        scheduleMenu.add(daily20);
        scheduleMenu.addSeparator();
        noSchedule = radioItem("不自动切换", font, scheduleGroup, () -> { actions.onScheduleChanged(0, false, 0); });
        scheduleMenu.add(noSchedule);
        popupMenu.add(scheduleMenu);

        // ── Auto start ──
        autoStartItem = new JMenuItem("开机自启");
        autoStartItem.setFont(font);
        autoStartItem.setIcon(createIconPower());
        autoStartItem.setBorder(BorderFactory.createEmptyBorder(3, 13, 3, 18));
        autoStartItem.addActionListener(e -> {
            autoStartEnabled = !autoStartEnabled;
            updateAutoStartText();
            actions.onAutoStartToggled(autoStartEnabled);
        });
        popupMenu.add(autoStartItem);

        // ── Cleanup submenu ──
        JMenu cleanupMenu = new JMenu("自动清理");
        cleanupMenu.setFont(font);
        cleanupMenu.setIcon(createIconTrash());
        cleanupMenu.setBorder(BorderFactory.createEmptyBorder(3, 13, 3, 18));

        ButtonGroup retainGroup = new ButtonGroup();
        retain3    = radioItem("保留 3 天", font, retainGroup, () -> { actions.onRetentionChanged(3); });
        retain7    = radioItem("保留 7 天", font, retainGroup, () -> { actions.onRetentionChanged(7); });
        retain30   = radioItem("保留 30 天", font, retainGroup, () -> { actions.onRetentionChanged(30); });
        retain90   = radioItem("保留 90 天", font, retainGroup, () -> { actions.onRetentionChanged(90); });
        retain180  = radioItem("保留 180 天", font, retainGroup, () -> { actions.onRetentionChanged(180); });
        retain360  = radioItem("保留 360 天", font, retainGroup, () -> { actions.onRetentionChanged(360); });
        retainNever = radioItem("不自动清理", font, retainGroup, () -> { actions.onRetentionChanged(0); });
        cleanupMenu.add(retain3);
        cleanupMenu.add(retain7);
        cleanupMenu.add(retain30);
        cleanupMenu.add(retain90);
        cleanupMenu.add(retain180);
        cleanupMenu.add(retain360);
        cleanupMenu.addSeparator();
        cleanupMenu.add(retainNever);
        popupMenu.add(cleanupMenu);

        // ── Resolution submenu ──
        JMenu resolutionMenu = new JMenu("壁纸尺寸");
        resolutionMenu.setFont(font);
        resolutionMenu.setIcon(createIconResolution());
        resolutionMenu.setBorder(BorderFactory.createEmptyBorder(3, 13, 3, 18));

        ButtonGroup resGroup = new ButtonGroup();
        res1080p = radioItem("1080P", font, resGroup, () -> { actions.onResolutionChanged("1080p"); });
        res1200p = radioItem("1920×1200", font, resGroup, () -> { actions.onResolutionChanged("1200p"); });
        res4k    = radioItem("4K", font, resGroup, () -> { actions.onResolutionChanged("4k"); });
        resolutionMenu.add(res1080p);
        resolutionMenu.add(res1200p);
        resolutionMenu.add(res4k);
        popupMenu.add(resolutionMenu);

        popupMenu.addSeparator();

        // ── Exit ──
        popupMenu.add(buildMenuItem("退出", createIconExit(), font, () -> {
            hiddenDialog.setVisible(false); actions.onExit();
        }));

        // ── Tray icon ──
        Image image = createTrayIconImage();
        trayIcon = new TrayIcon(image, "Bing 壁纸");
        trayIcon.setImageAutoSize(true);

        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
                else if (e.getButton() == MouseEvent.BUTTON1) {
                    actions.onBrowseHistory();
                }
            }
            private void showPopup(MouseEvent e) {
                int x = e.getXOnScreen();
                int y = e.getYOnScreen() - popupMenu.getPreferredSize().height;
                popupMenu.setInvoker(hiddenDialog);
                popupMenu.setLocation(x, y);
                hiddenDialog.setLocation(x, y);
                hiddenDialog.setVisible(true);
                popupMenu.setVisible(true);
            }
        });

        try {
            SystemTray.getSystemTray().add(trayIcon);
            updateMenuState(false, false);
        } catch (AWTException e) {
            LOG.log(java.util.logging.Level.SEVERE, "Failed to add tray icon", e);
        }
    }

    /** Create a radio menu item wired to a button group and action. */
    private JRadioButtonMenuItem radioItem(String text, Font font, ButtonGroup group, Runnable action) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(text);
        item.setFont(font);
        item.setBorder(BorderFactory.createEmptyBorder(3, 13, 3, 18));
        group.add(item);
        item.addActionListener(e -> { if (item.isSelected()) action.run(); });
        return item;
    }

    /** Create a plain menu item with optional icon, wired to an action. */
    private static JMenuItem buildMenuItem(String text, Icon icon, Font font, Runnable action) {
        JMenuItem item = icon != null ? new JMenuItem(text, icon) : new JMenuItem(text);
        item.setFont(font);
        item.setBorder(BorderFactory.createEmptyBorder(3, 13, 3, 18));
        item.addActionListener(e -> action.run());
        return item;
    }

    // ── Menu icon generators ──────────────────────────────────────────

    private static final int MENU_ICON_SIZE = 27;

    /** Scale a programmatic icon to menu size synchronously. */
    private static ImageIcon menuIcon(BufferedImage src) {
        BufferedImage scaled = new BufferedImage(MENU_ICON_SIZE, MENU_ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, MENU_ICON_SIZE, MENU_ICON_SIZE, null);
        g.dispose();
        return new ImageIcon(scaled);
    }

    /** Previous arrow icon. */
    private static ImageIcon createIconPrev() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0, 90, 200));
        g.fillPolygon(new int[]{14, 2, 14}, new int[]{1, 8, 15}, 3);
        g.dispose();
        return menuIcon(img);
    }

    /** Next arrow icon. */
    private static ImageIcon createIconNext() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0, 90, 200));
        g.fillPolygon(new int[]{2, 14, 2}, new int[]{1, 8, 15}, 3);
        g.dispose();
        return menuIcon(img);
    }

    /** Refresh icon. */
    private static ImageIcon createIconUpdate() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0, 130, 0));
        g.fillOval(2, 2, 12, 12);
        g.setColor(Color.WHITE);
        g.fillPolygon(new int[]{6, 6, 12}, new int[]{4, 12, 8}, 3);
        g.dispose();
        return menuIcon(img);
    }

    /** Cross/X exit icon. */
    private static ImageIcon createIconExit() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(200, 40, 40));
        g.drawLine(3, 3, 13, 13);
        g.drawLine(13, 3, 3, 13);
        g.dispose();
        return menuIcon(img);
    }

    /** Clock icon for schedule menu. */
    private static ImageIcon createIconClock() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(80, 80, 80));
        g.drawOval(2, 2, 12, 12);
        g.drawLine(8, 8, 8, 4);
        g.drawLine(8, 8, 11, 8);
        g.dispose();
        return menuIcon(img);
    }

    /** Power icon for auto-start toggle. */
    private static ImageIcon createIconPower() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0, 130, 0));
        g.fillOval(2, 2, 12, 12);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(5, 8, 7, 10);
        g.drawLine(7, 10, 11, 5);
        g.dispose();
        return menuIcon(img);
    }

    /** Trash can icon for cleanup menu. */
    private static ImageIcon createIconTrash() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(80, 80, 80));
        // Lid
        g.drawLine(3, 5, 13, 5);
        // Handle
        g.drawArc(6, 1, 4, 4, 0, 180);
        // Body
        g.drawRoundRect(3, 5, 10, 9, 1, 1);
        g.dispose();
        return menuIcon(img);
    }

    /** Monitor icon for resolution menu. */
    private static ImageIcon createIconResolution() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0, 100, 180));
        // Monitor screen
        g.drawRoundRect(1, 2, 14, 10, 2, 2);
        // Stand
        g.drawLine(5, 12, 11, 12);
        g.drawLine(8, 12, 8, 14);
        g.drawLine(5, 14, 11, 14);
        g.dispose();
        return menuIcon(img);
    }

    /** Shared by HistoryWindow for its window icon. */
    public static Image createTrayIconImage() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0, 120, 215));
        g.fillRoundRect(0, 0, 32, 32, 8, 8);
        g.setColor(new Color(255, 193, 7));
        g.fillOval(5, 4, 16, 16);
        g.setColor(new Color(76, 175, 80));
        g.fillPolygon(new int[]{-4, 16, 36}, new int[]{36, 6, 36}, 3);
        g.setColor(new Color(56, 142, 60));
        g.fillPolygon(new int[]{6, 24, 36}, new int[]{36, 20, 36}, 3);
        g.dispose();
        return image;
    }
}
