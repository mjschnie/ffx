/**
 * Title: Force Field X.
 *
 * Description: Force Field X - Software for Molecular Biophysics.
 *
 * Copyright: Copyright (c) Michael J. Schnieders 2001-2013.
 *
 * This file is part of Force Field X.
 *
 * Force Field X is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * Force Field X is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Force Field X; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 */
package ffx.ui.macosx;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;

import com.apple.eawt.Application;
import com.apple.eawt.ApplicationAdapter;
import com.apple.eawt.ApplicationEvent;

import org.apache.commons.lang3.builder.ToStringBuilder;

import ffx.ui.MainPanel;

/**
 * The OSXAdapter class was developed by following an example supplied on the OS
 * X site. It handles events generated by the following standard OS X toolbar
 * items: About, Preferences, Quit and File Associations
 *
 * @author Michael J. Schnieders
 *
 */
public class OSXAdapter extends ApplicationAdapter {

    private MainPanel mainPanel;
    private static OSXAdapter adapter;
    private static Application application;
    private static final Logger logger = Logger.getLogger(OSXAdapter.class.getName());

    /**
     * <p>registerMacOSXApplication</p>
     *
     * @param m a {@link ffx.ui.MainPanel} object.
     */
    public static void registerMacOSXApplication(MainPanel m) {
        if (application == null) {
            application = Application.getApplication();
        }
        if (adapter == null) {
            adapter = new OSXAdapter(m);
        }
        application.addApplicationListener(adapter);
        application.setEnabledPreferencesMenu(true);
        application.setEnabledAboutMenu(true);
        ImageIcon icon = new ImageIcon(m.getClass().getClassLoader().getResource("ffx/ui/icons/icon64.png"));
        application.setDockIconImage(icon.getImage());
    }

    /**
     * <p>macOSXRegistration</p>
     *
     * @param m a {@link ffx.ui.MainPanel} object.
     */
    public static void macOSXRegistration(MainPanel m) {
        try {
            String name = OSXAdapter.class.getName();
            Class adapterClass = Class.forName(name);
            Class[] defArgs = {MainPanel.class};
            Method registerMethod = adapterClass.getDeclaredMethod(
                    "registerMacOSXApplication", defArgs);
            if (registerMethod != null) {
                Object[] args = {m};
                registerMethod.invoke(adapterClass, args);
            }
        } catch (NoClassDefFoundError e) {
            logger.log(Level.WARNING, "\nThis version of Mac OS X does not support "
                    + "the Apple EAWT.  Application Menu handling "
                    + "has been disabled\n", e);
        } catch (ClassNotFoundException e) {
            logger.log(Level.WARNING, "\nThis version of Mac OS X does not support "
                    + "the Apple EAWT.  Application Menu handling "
                    + "has been disabled\n", e);
        } catch (Exception e) {
            logger.log(Level.WARNING, "\nException while loading the OSXAdapter", e);
        }
    }

    /**
     * Set Mac OS X Systems Properties to promote native integration. How soon
     * do these need to be set to be recognized?
     */
    public static void setOSXProperties() {

        System.setProperty("apple.mrj.application.apple.menu.about.name", "Force Field X");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.showGrowBox", "true");
        System.setProperty("apple.mrj.application.growbox.intrudes", "false");
        System.setProperty("apple.awt.brushMetalLook", "true");
        System.setProperty("apple.mrj.application.live-resize", "true");
        System.setProperty("apple.macos.smallTabs", "true");

        // -Xdock:name="Force Field X"
    }

    private OSXAdapter(MainPanel m) {
        mainPanel = m;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleAbout(ApplicationEvent ae) {
        if (mainPanel != null) {
            ae.setHandled(true);
            mainPanel.about();
        } else {
            ae.setHandled(false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleOpenFile(ApplicationEvent ae) {
        if (mainPanel != null) {
            mainPanel.open(ae.getFilename());
            ae.setHandled(true);
        } else {
            ae.setHandled(false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handlePreferences(ApplicationEvent ae) {
        if (mainPanel != null) {
            mainPanel.getGraphics3D().preferences();
            ae.setHandled(true);
        } else {
            ae.setHandled(false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleQuit(ApplicationEvent ae) {
        if (mainPanel != null) {
            ae.setHandled(false);
            mainPanel.exit();
        } else {
            System.exit(-1);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this).append(application).toString();
    }
}
