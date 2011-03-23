/**
 * Title: Force Field X
 * Description: Force Field X - Software for Molecular Biophysics.
 * Copyright: Copyright (c) Michael J. Schnieders 2001-2011
 *
 * This file is part of Force Field X.
 *
 * Force Field X is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as published
 * by the Free Software Foundation.
 *
 * Force Field X is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Force Field X; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */
package ffx;

import static java.lang.String.format;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.UIManager;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.time.StopWatch;

import ffx.ui.LogHandler;
import ffx.ui.MainPanel;
import ffx.ui.macosx.OSXAdapter;

/**
 * The Main class is the entry point to the graphical user interface version of
 * Force Field X.
 *
 * @author Michael J. Schnieders
 * @since 1.0
 */
public class Main extends JFrame {

    private static final Logger logger = Logger.getLogger(Main.class.getName());
    private static final Level level;
    private static final LogHandler logHandler;

    static {
        /*
         * Remove the default console handler from the root logger.
         */
        try {
            Logger defaultLogger = LogManager.getLogManager().getLogger("");
            Handler defaultHandlers[] = defaultLogger.getHandlers();
            for (Handler h : defaultHandlers) {
                defaultLogger.removeHandler(h);
            }
        } catch (Exception e) {
            System.err.println(e.toString());
        }

        /**
         * Create a Handler for FFX logging.
         */
        String logLevel = System.getProperty("ffx.log", "info");
        Level tempLevel;
        try {
            tempLevel = Level.parse(logLevel.toUpperCase());
        } catch (Exception e) {
            tempLevel = Level.INFO;
        }
        level = tempLevel;
        logHandler = new LogHandler();
        logHandler.setLevel(level);
        Logger ffxLogger = Logger.getLogger(Main.class.getPackage().getName());
        ffxLogger.addHandler(logHandler);
        ffxLogger.setLevel(level);
    }

    /**
     * Create an instance of Force Field X
     */
    public static void main(String[] args) throws Exception {

        // If a file was supplied on the command line, get its absolute path
        File commandLineFile = null;
        List<String> argList = new ArrayList<String>();
        if (args != null && args.length > 0) {
            commandLineFile = new File(args[0]);
            // Resolve a relavtive path
            if (commandLineFile.exists()) {
                commandLineFile = new File(FilenameUtils.normalize(
                        commandLineFile.getAbsolutePath()));
            }
            // Convert the of the args to a list
            int nArgs = args.length;
            if (nArgs > 1) {
                for (int i = 1; i < nArgs; i++) {
                    argList.add(args[i]);
                }
            }
        }
        logger.info(MainPanel.border);
        logger.info(MainPanel.title);
        logger.info(MainPanel.aboutString);
        logger.info(MainPanel.border);
        if (!GraphicsEnvironment.isHeadless()) {
            logger.info("\n Starting up the graphical user interface");
            // Some Mac OS X specific features that help FFX look native.
            // These need to be set before the MainPanel is created.
            if (SystemUtils.IS_OS_MAC_OSX) {
                OSXAdapter.setOSXProperties();
            }
            // Set some Swing Constants
            UIManager.put("swing.boldMetal", Boolean.FALSE);
            setDefaultLookAndFeelDecorated(false);
            // Initialize the main frame and Force Field X MainPanel
            Main m = new Main(commandLineFile, argList);
        } else {
            logger.info("\n Starting up the command line interface");
            HeadlessMain m = new HeadlessMain(commandLineFile, argList, logHandler);
        }
        logger.info(" Log level is set to " + level.toString());
    }

    /**
     * Main does some window initializations.
     */
    public Main(File commandLineFile, List<String> argList) {
        super("Force Field X");
        // Start the clock.
        stopWatch.start();
        setVisible(false);

        // Create the MainPanel and MainMenu, then add them to the JFrame
        java.awt.Toolkit.getDefaultToolkit().setDynamicLayout(true);
        mainPanel = new MainPanel(this);
        logHandler.setMainPanel(mainPanel);
        add(mainPanel);
        mainPanel.initialize();
        setJMenuBar(mainPanel.getMainMenu());
        // Set the Title and Icon
        setTitle("Force Field X");
        URL iconURL = getClass().getClassLoader().getResource(
                "ffx/ui/icons/icon64.png");
        ImageIcon icon = new ImageIcon(iconURL);
        setIconImage(icon.getImage());
        addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                if (mainPanel != null) {
                    mainPanel.exit();
                }
                System.exit(0);
            }
        });
        // This is a hack to get GraphicsCanvis to initialize on some
        // platform/Java3D combinations.
        mainPanel.setPanel(MainPanel.KEYWORDS);
        setVisible(true);
        mainPanel.setPanel(MainPanel.GRAPHICS);
        // Mac OS X specific features that help Force Field X look native
        // on Macs. This needs to be done after the MainPanel is created.
        if (SystemUtils.IS_OS_MAC_OSX) {
            OSXAdapter.macOSXRegistration(mainPanel);
        }
        // Finally, open the supplied file if necessary.
        if (commandLineFile != null && !commandLineFile.exists()) {
            /**
             * See if the commandLineFile is an embedded script.
             */
            String name = commandLineFile.getName() + ".ffx";
            ClassLoader loader = getClass().getClassLoader();
            URL embeddedScript = loader.getResource("ffx/scripts/" + name);
            if (embeddedScript != null) {
                try {
                    commandLineFile = new File(
                            FFXClassLoader.copyInputStreamToTmpFile(
                            embeddedScript.openStream(), ".ffx"));
                } catch (Exception e) {
                    logger.warning("Exception extracting embedded script "
                            + embeddedScript.toString() + "\n" + e.toString());
                }
            }
        }
        if (commandLineFile != null) {
            if (commandLineFile.exists()) {
                mainPanel.getModelingShell().setArgList(argList);
                mainPanel.open(commandLineFile, null);
            } else {
                logger.warning(format("%s was not found.", commandLineFile.toString()));
            }
        }
        if (System.getProperty("ffx.timer") != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(format("\n Start-up Time (msec): %s.", stopWatch.getTime()));
            Runtime runtime = Runtime.getRuntime();
            runtime.runFinalization();
            runtime.gc();
            long occupiedMemory = runtime.totalMemory() - runtime.freeMemory();
            long KB = 1024;
            sb.append(format("\n In-Use Memory   (Kb): %d", occupiedMemory / KB));
            sb.append(format("\n Free Memory     (Kb): %d", runtime.freeMemory() / KB));
            sb.append(format("\n Total Memory    (Kb): %d", runtime.totalMemory() / KB));
            logger.info(sb.toString());
        }
    }

    /**
     * Commons.Lang Style toString.
     */
    @Override
    public String toString() {
        ToStringBuilder toStringBuilder = new ToStringBuilder(this).append(
                "Up Time: " + stopWatch).append("Logger: " + logger.getName());
        return toStringBuilder.toString();
    }
    /**
     * This is the main application wrapper.
     */
    public MainPanel mainPanel;
    public static StopWatch stopWatch = new StopWatch();
}
