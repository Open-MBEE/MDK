package gov.nasa.jpl.mbee.mdk;

import com.nomagic.actions.ActionsCategory;
import com.nomagic.actions.ActionsManager;
import com.nomagic.actions.NMAction;
import com.nomagic.magicdraw.actions.ActionsConfiguratorsManager;
import com.nomagic.magicdraw.commandline.CommandLineActionManager;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.options.EnvironmentOptions;
import com.nomagic.magicdraw.evaluation.EvaluationConfigurator;
import com.nomagic.magicdraw.plugins.Plugin;
import com.nomagic.magicdraw.plugins.PluginDescriptor;
import com.nomagic.magicdraw.plugins.PluginUtils;
import com.nomagic.magicdraw.uml.DiagramDescriptor;
import com.nomagic.magicdraw.uml.DiagramTypeConstants;
import gov.nasa.jpl.mbee.mdk.cli.AutomatedCommitter;
import gov.nasa.jpl.mbee.mdk.cli.AutomatedViewGenerator;
import gov.nasa.jpl.mbee.mdk.mms.sync.status.SyncStatusConfigurator;
import gov.nasa.jpl.mbee.mdk.options.MDKOptionsGroup;
import gov.nasa.jpl.mbee.mdk.util.MDUtils;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MDKPlugin extends Plugin {
    public static final String MAIN_TOOLBAR_CATEGORY_NAME = "MDK";

    private static MDKPlugin INSTANCE;
    private static boolean JAVAFX_SUPPORTED;

    public static ClassLoader extensionsClassloader;
    public static ActionsManager MAIN_TOOLBAR_ACTIONS_MANAGER;

    public MDKPlugin() {
        super();
    }

    public static MDKPlugin getInstance() {
        if (INSTANCE == null) {
            INSTANCE = PluginUtils.getPlugins().stream()
                    .filter(plugin -> Objects.equals(plugin.getDescriptor().getName(), "Model Development Kit"))
                    .filter(plugin -> plugin instanceof MDKPlugin)
                    .map(plugin -> (MDKPlugin) plugin)
                    .findAny()
                    .orElseThrow(IllegalStateException::new);
        }
        return INSTANCE;
    }

    @Deprecated
    public static String getVersion() {
        return getInstance().getDescriptor().getVersion();
    }

    public static void updateMainToolbarCategory() {
        if (MAIN_TOOLBAR_ACTIONS_MANAGER == null) {
            return;
        }
        ActionsCategory category = MAIN_TOOLBAR_ACTIONS_MANAGER.getCategory(MAIN_TOOLBAR_CATEGORY_NAME);
        if (category == null) {
            return;
        }
        List<NMAction> actions = new ArrayList<>(category.getActions());
        for (NMAction action : actions) {
            category.removeAction(action);
        }
        for (NMAction action : actions) {
            category.addAction(action);
        }
    }

    @Override
    public boolean close() {
        return true;
    }

    @Override
    public void init() {
        ActionsConfiguratorsManager acm = ActionsConfiguratorsManager.getInstance();
        System.setProperty("jsse.enableSNIExtension", "false");
        if (MDUtils.isDeveloperMode()) {
            System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
            System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
            System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "INFO");
        }
        // This somehow allows things to be loaded to evaluate opaque expressions or something.
        EvaluationConfigurator.getInstance().registerBinaryImplementers(this.getClass().getClassLoader());

        CommandLineActionManager.getInstance().addAction(new AutomatedViewGenerator());
        CommandLineActionManager.getInstance().addAction(new AutomatedCommitter());

        MDKConfigurator mdkConfigurator = new MDKConfigurator();
        acm.addMainMenuConfigurator(mdkConfigurator);
        acm.addContainmentBrowserContextConfigurator(mdkConfigurator);
        acm.addSearchBrowserContextConfigurator(mdkConfigurator);
        acm.addBaseDiagramContextConfigurator(DiagramTypeConstants.UML_ANY_DIAGRAM, mdkConfigurator);

        acm.addMainMenuConfigurator(new MMSConfigurator());
        EvaluationConfigurator.getInstance().registerBinaryImplementers(MDKPlugin.class.getClassLoader());

        acm.addMainToolbarConfigurator(new SyncStatusConfigurator());

        DiagramDescriptor viewDiagramDescriptor = Application.getInstance().getDiagramDescriptor(ViewDiagramConfigurator.DIAGRAM_NAME);
        if (viewDiagramDescriptor != null) {
            ActionsConfiguratorsManager actionsConfiguratorsManager = ActionsConfiguratorsManager.getInstance();
            ViewDiagramConfigurator viewDiagramConfigurator = new ViewDiagramConfigurator();
            actionsConfiguratorsManager.addDiagramToolbarConfigurator(ViewDiagramConfigurator.DIAGRAM_NAME, viewDiagramConfigurator);
            actionsConfiguratorsManager.addTargetElementAMConfigurator(ViewDiagramConfigurator.DIAGRAM_NAME, viewDiagramConfigurator);
        }

        EvaluationConfigurator.getInstance().registerBinaryImplementers(MDKPlugin.class.getClassLoader());

        MMSSyncPlugin.getInstance().init();

        loadExtensionJars();
        configureEnvironmentOptions();
        initJavaFX();
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    private void loadExtensionJars() {
        File extensionDir = new File(getDescriptor().getPluginDirectory(), "extensions");
        if (!extensionDir.exists()) {
            extensionsClassloader = MDKPlugin.class.getClassLoader();
            return;
        }
        List<URL> extensions = new ArrayList<URL>();
        try {
            extensions.add(extensionDir.toURI().toURL());
        } catch (MalformedURLException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        File[] files = extensionDir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                extensions.add(file.toURI().toURL());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
        extensionsClassloader = new URLClassLoader(extensions.toArray(new URL[]{}),
                MDKPlugin.class.getClassLoader());
    }

    private void configureEnvironmentOptions() {
        EnvironmentOptions mdkOptions = Application.getInstance().getEnvironmentOptions();
        mdkOptions.addGroup(new MDKOptionsGroup());
    }

    private void initJavaFX() {
        try {
            Class.forName("javafx.application.Platform");
        } catch (ClassNotFoundException e) {
            System.err.println("[WARNING] JavaFX libraries are unavailable. Please add \"-Dorg.osgi.framework.bundle.parent=ext\" to the \"JAVA_ARGS\" line in the properties file(s) in your MagicDraw bin directory and restart.");
            return;
        }
        new Thread(() -> {
            try {
                Class<?> clazz = Class.forName("gov.nasa.jpl.mbee.mdk.MDKApplication");
                Method method = clazz.getMethod("main", String[].class);
                // has to be before invocation since it hangs
                MDKPlugin.JAVAFX_SUPPORTED = true;
                method.invoke(null, new Object[]{new String[]{}});
            } catch (Exception | Error e) {
                MDKPlugin.JAVAFX_SUPPORTED = false;
                System.err.println("[WARNING] Failed to initialize JavaFX application. JavaFX functionality is disabled.");
                e.printStackTrace();
            }
        }, "JavaFX Init").start();
    }

    public static boolean isJavaFXSupported() {
        return JAVAFX_SUPPORTED;
    }
}
