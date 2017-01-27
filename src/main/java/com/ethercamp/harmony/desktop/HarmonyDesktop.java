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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.Arrays.stream;

/**
 * Created by Stan Reshetnyk on 25.01.17.
 */
public class HarmonyDesktop {

    static {
        System.setProperty("logs.dir", System.getProperty("user.home") + "/ethereumj/harmony-logs");
        System.setProperty("logback.configurationFile", "src/main/resources/logback.xml");

        // Hide Dock icon on Mac
        System.setProperty("apple.awt.UIElement", "true");
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

    final MenuItem browserMenu = new MenuItem("Open browser");
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
                log.info("Spring context created at port1 " + serverPort);
                System.out.println("Spring context created at port2 " + serverPort);
                trayIcon.setImage(new ImageIcon(imageEnabledUrl).getImage());
                setTrayMenu(trayIcon, browserMenu, quitMenu);
                openBrowser();

            } catch (Exception e) {
                final StringBuilder sb = new StringBuilder(e.toString());
                for (StackTraceElement ste : e.getStackTrace()) {
                    sb.append("\n\tat ");
                    sb.append(ste);
                }
                String message = sb.toString();
                showErrorWindow("", "Problem running Harmony:\n\n " + message);
            }
        });

        if (!SystemTray.isSupported()) {
            log.error("System tray is not supported");
            return;
        }

        browserMenu.addActionListener(e -> openBrowser());
        quitMenu.addActionListener(event -> {
            log.info("Quit action was requested from tray menu");
            closeContext();
            trayIcon.setImage(new ImageIcon(imageDisabledUrl).getImage());
            setTrayMenu(trayIcon, quitingMenu);
            System.exit(0);
        });

        setTrayMenu(trayIcon, loadingMenu, quitMenu);
    }

    private void closeContext() {
        if (context != null) {
            try {
                context.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void showErrorWindow(String title, String body) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            JOptionPane.showMessageDialog(null, body, null, JOptionPane.PLAIN_MESSAGE);
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openBrowser() {
        openBrowser("http://localhost:" + serverPort);
    }

    private static void setTrayMenu(TrayIcon trayIcon, MenuItem ...items) {
        if (!SystemTray.isSupported()) {
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
            e.printStackTrace();
        }
    }

    private static void openBrowser(String url) {
        Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
            try {
                desktop.browse(URI.create(url));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
