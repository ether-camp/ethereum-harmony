/*
 * Copyright 2015, 2016 Ether.Camp Inc. (US)
 * This file is part of Ethereum Harmony.
 *
 * Ethereum Harmony is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Ethereum Harmony is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Ethereum Harmony.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ethercamp.harmony.desktop;

import com.ethercamp.harmony.Application;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.Arrays.stream;

/**
 * Created by Stan Reshetnyk on 25.01.17
 */
public class HarmonyDesktop {

    private static String LOGS_PATH = System.getProperty("user.home") + "/ethereumj/harmony-logs";

    static {
        System.setProperty("logs.dir", LOGS_PATH);
        System.setProperty("logback.configurationFile", "src/main/resources/logback.xml");

        // Hide Dock icon on Mac
        System.setProperty("apple.awt.UIElement", "true");

        System.setProperty("database.dir", System.getProperty("user.home") + "/ethereumj/database");
    }

    private final static Logger log = LoggerFactory.getLogger("desktop");

    private volatile int serverPort = 8080;

    private volatile ConfigurableApplicationContext context;

    public static void main(String[] args) throws Exception {
        new HarmonyDesktop().start();
    }

    ExecutorService executor = Executors.newSingleThreadExecutor();

    final MenuItem loadingMenu = new MenuItem("Starting...") {{setEnabled(false);}};
    final MenuItem quitingMenu = new MenuItem("Quitting...") {{setEnabled(false);}};

    final MenuItem browserMenu = new MenuItem("Open Browser");
    final MenuItem logsMenu = new MenuItem("Open Logs");
    final MenuItem quitMenu = new MenuItem("Quit");

    final URL imageDisabledUrl = ClassLoader.getSystemResource("desktop/camp-disabled-icon-2x.png");
    final URL imageEnabledUrl = ClassLoader.getSystemResource("desktop/camp-icon-2x.png");


    private void start() throws Exception {
        log.info("Starting...");

        final TrayIcon trayIcon = new TrayIcon(new ImageIcon(imageDisabledUrl).getImage(), "Ethereum Harmony");
        trayIcon.setImageAutoSize(true);    // Auto-size icon base on space

        // doesn't work
//        Runtime.getRuntime().addShutdownHook(new Thread(() -> closeContext()));

        executor.submit(() -> {
            try {
                context = new SpringApplicationBuilder(Application.class)
                        .headless(true)
                        .web(true)
                        .run();
                serverPort = Integer.valueOf(context.getEnvironment().getProperty("local.server.port"));
                log.info("Spring context created at port " + serverPort);
                trayIcon.setImage(new ImageIcon(imageEnabledUrl).getImage());
                setTrayMenu(trayIcon, browserMenu, logsMenu, quitMenu);
                openBrowser();

            } catch (Exception e) {
                final Throwable cause = DesktopUtil.findCauseFromSpringException(e);
                showErrorWindow(cause.getMessage(), "Problem running Harmony:\n\n"
                        + ExceptionUtils.getStackTrace(cause));
            }
        });

        if (!SystemTray.isSupported()) {
            log.error("System tray is not supported");
            return;
        }

        browserMenu.addActionListener(e -> openBrowser());
        quitMenu.addActionListener(event -> {
            log.info("Quit action was requested from tray menu");
            trayIcon.setImage(new ImageIcon(imageDisabledUrl).getImage());
            setTrayMenu(trayIcon, logsMenu, quitingMenu);
            closeContext();
            System.exit(0);
        });

        logsMenu.addActionListener(event -> {
            log.info("Logs action was requested from tray menu");
            final File logsFile = new File(LOGS_PATH);
            try {
                Desktop.getDesktop().open(logsFile);
            } catch (IOException e) {
                log.error("Problem opening logs dir", e);
            }
        });

        setTrayMenu(trayIcon, loadingMenu, logsMenu, quitMenu);
    }

    private void closeContext() {
        if (context != null) {
            try {
                context.close();
            } catch (Exception e) {
                log.error("Problem closing context: " + e.getMessage(), e);
            }
        }
    }

    private void showErrorWindow(String title, String body) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//            System.setProperty("apple.awt.UIElement", "false");

            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

            JTextArea textArea = new JTextArea(body);
            JScrollPane scrollPane = new JScrollPane(textArea);
            textArea.setLineWrap(true);
            textArea.setFont(Font.getFont(Font.MONOSPACED));
            textArea.setEditable(false);
            textArea.setWrapStyleWord(true);
            scrollPane.setPreferredSize( new Dimension( 500, 500 ) );

            JTextPane titleLabel = new JTextPane();
            titleLabel.setContentType("text/html"); // let the text pane know this is what you want
            titleLabel.setText("<html>" + "<b>" + title + "</b>" +  "</html>"); // showing off
            titleLabel.setEditable(false);
            titleLabel.setBackground(null);
            titleLabel.setBorder(null);

            panel.add(titleLabel);
            panel.add(scrollPane);

            final JFrame frame = new JFrame();
            frame.setAlwaysOnTop(true);
            moveCenter(frame);
            frame.setVisible(true);

            JOptionPane.showMessageDialog(frame, panel, "Oops. Ethereum Harmony stopped with error.",
                    JOptionPane.CLOSED_OPTION);
            System.exit(1);
        } catch (Exception e) {
            log.error("Problem showing error window", e);
        }
    }

    private static void moveCenter(JFrame frame) {
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setLocation(dim.width / 2 - frame.getSize().width / 2, dim.height / 2 - frame.getSize().height / 2);
    }

    private void openBrowser() {
        openBrowser("http://localhost:" + serverPort);
    }

    private static void setTrayMenu(TrayIcon trayIcon, MenuItem ...items) {
        if (!SystemTray.isSupported()) {
            log.error("System tray is not supported");
            return;
        }
        final SystemTray systemTray = SystemTray.getSystemTray();

        final PopupMenu popupMenu = new PopupMenu();
        stream(items).forEach(i -> popupMenu.add(i));
        trayIcon.setPopupMenu(popupMenu);

        try {
            stream(systemTray.getTrayIcons()).forEach(t -> systemTray.remove(t));
            systemTray.add(trayIcon);
        } catch (AWTException e) {
            log.error("Problem set tray", e);
        }
    }

    private static void openBrowser(String url) {
        Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
            try {
                desktop.browse(URI.create(url));
            } catch (Exception e) {
                log.error("Problem opening browser", e);
            }
        }
    }
}
