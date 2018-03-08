package gov.nasa.jpl.mbee.mdk.model;

import com.nomagic.magicdraw.ui.browser.WindowComponentContent;
import com.tomsawyer.magicdraw.action.TSMDPluginModelViewportProvider;
import com.tomsawyer.view.swing.TSSwingView;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class TSStandardWindowComponentContent implements WindowComponentContent {
    private final JSplitPane mainPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

    public TSStandardWindowComponentContent(TSMDPluginModelViewportProvider windowProvider) {
        mainPanel.setTopComponent(this.newComponentForViews(windowProvider.getTopPanelViews()));
        mainPanel.setBottomComponent(this.newComponentForViews(windowProvider.getBottomPanelViews()));
    }

    /**
     * This method creates an appropriate container for a list of views.
     *
     * @param panelViews The list of views.
     * @return The component containing the listed views.
     */
    private Component newComponentForViews(List<TSSwingView> panelViews) {
        Component result;
        if (panelViews.size() == 1) {
            result = panelViews.get(0).getComponent();
        }
        else {
            JTabbedPane tabbedPane = new JTabbedPane();
            for (TSSwingView view : panelViews) {
                this.addTab(tabbedPane, view);
            }
            result = tabbedPane;
        }
        return result;
    }

    void setDividerLocation(double dividerLocation) {
        this.mainPanel.setDividerLocation(dividerLocation);
    }

    /**
     * This helper method adds a swing view to tabbed pane.
     *
     * @param tabbedPane The tabbed pane
     * @param view       The swing view
     */
    protected void addTab(JTabbedPane tabbedPane, TSSwingView view) {
        if (view != null) {
            String name = view.getViewDefinition().getLabel();
            if (name == null || name.isEmpty()) {
                name = view.getViewDefinition().getName();
            }
            tabbedPane.add(name, (Component) view.asWidget());
        }
    }

    @Override
    public Component getWindowComponent() {
        return this.mainPanel;
    }

    @Override
    public Component getDefaultFocusComponent() {
        return null;
    }
}