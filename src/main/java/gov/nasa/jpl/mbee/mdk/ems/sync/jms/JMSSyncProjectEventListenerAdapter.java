package gov.nasa.jpl.mbee.mdk.ems.sync.jms;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.core.ProjectUtilities;
import com.nomagic.magicdraw.core.project.ProjectEventListenerAdapter;
import com.nomagic.magicdraw.esi.EsiUtils;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import gov.nasa.jpl.mbee.mdk.ems.ServerException;
import gov.nasa.jpl.mbee.mdk.ems.actions.EMSLoginAction;
import gov.nasa.jpl.mbee.mdk.ems.jms.JMSUtils;
import gov.nasa.jpl.mbee.mdk.lib.MDUtils;
import gov.nasa.jpl.mbee.mdk.lib.TicketUtils;
import gov.nasa.jpl.mbee.mdk.options.MDKOptionsGroup;

import javax.jms.*;
import javax.naming.NameNotFoundException;
import java.lang.IllegalStateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by igomes on 6/28/16.
 */
public class JMSSyncProjectEventListenerAdapter extends ProjectEventListenerAdapter {
    private static final String ERROR_STRING = "Reverting to offline mode. All changes will be saved in the model until reconnected.";
    private static final Map<String, JMSSyncProjectMapping> projectMappings = new ConcurrentHashMap<>();

    @Override
    public void projectOpened(final Project project) {
        closeJMS(project);
        if (shouldEnableJMS(project)) {
            new Thread() {
                public void run() {
                    if (TicketUtils.isTicketValid()) {
                        initializeJMS(project);
                    } else {
                        EMSLoginAction.loginAction();
                        // loginAction contains a call to initializeJMS on a successful ticket get
                    }
                }
            }.start();
        }
    }

    @Override
    public void projectClosed(Project project) {
        closeJMS(project);
    }

    @Override
    public void projectReplaced(Project oldProject, Project newProject) {
        projectClosed(oldProject);
        projectOpened(newProject);
    }

    @Override
    public void projectSaved(Project project, boolean savedInServer) {
        JMSSyncProjectMapping jmsSyncProjectMapping = getProjectMapping(project);

        JMSMessageListener jmsMessageListener = jmsSyncProjectMapping.getJmsMessageListener();
        if (jmsMessageListener != null) {
            jmsMessageListener.getInMemoryJMSChangelog().clear();
        }
    }

    public static boolean shouldEnableJMS(Project project) {
        return ((project.getPrimaryModel() != null) && project.isRemote()
                && MDKOptionsGroup.getMDKOptions().isChangeListenerEnabled()
                && StereotypesHelper.hasStereotype(project.getPrimaryModel(), "ModelManagementSystem"));
    }

    public void initializeJMS(Project project) {
        JMSSyncProjectMapping jmsSyncProjectMapping = getProjectMapping(project);
        if (!shouldEnableJMS(project)) {
            jmsSyncProjectMapping.getJmsMessageListener().setDisabled(true);
            return;
        }
        boolean initialized = initDurable(project);
        jmsSyncProjectMapping.getJmsMessageListener().setDisabled(!initialized);
    }

    public void closeJMS(Project project) {
        JMSSyncProjectMapping jmsSyncProjectMapping = getProjectMapping(project);
        try {
            if (jmsSyncProjectMapping.getMessageConsumer() != null) {
                jmsSyncProjectMapping.getMessageConsumer().close();
            }
            if (jmsSyncProjectMapping.getSession() != null) {
                jmsSyncProjectMapping.getSession().close();
            }
            if (jmsSyncProjectMapping.getConnection() != null) {
                jmsSyncProjectMapping.getConnection().close();
            }
        } catch (JMSException e) {
            e.printStackTrace();
        }
        projectMappings.remove(project.getPrimaryProject().getProjectID());
    }

    public boolean initDurable(Project project) {
        String projectID = project.getPrimaryProject().getProjectID();
        String workspaceID = MDUtils.getWorkspace(project);

        // verify logged in to appropriate places
        if (!TicketUtils.isTicketSet()) {
            Application.getInstance().getGUILog().log("[WARNING] " + project.getName() + " - " + ERROR_STRING + " Reason: You must be logged into MMS.");
            return false;
        }
        if (ProjectUtilities.isFromEsiServer(project.getPrimaryProject())) {
            if (EsiUtils.getTeamworkService().getConnectedUser() == null) {
                Application.getInstance().getGUILog().log("[WARNING] " + project.getName() + " - " + ERROR_STRING + " Reason: You must be logged into Teamwork Cloud.");
                return false;
            }
        }

        // get jms connection info and connect
        JMSUtils.JMSInfo jmsInfo;
        try {
            jmsInfo = JMSUtils.getJMSInfo(project);
        } catch (ServerException | IllegalArgumentException | IllegalStateException e) {
            e.printStackTrace();
            Application.getInstance().getGUILog().log("[WARNING] " + project.getName() + " - " + ERROR_STRING + " Reason: " + e.getMessage());
            return false;
        }
        String url = (jmsInfo != null ? jmsInfo.getUrl() : null);
        if (url == null) {
            Application.getInstance().getGUILog().log("[WARNING] " + project.getName() + " - " + ERROR_STRING + " Reason: Cannot get server URL.");
            return false;
        }
        if (workspaceID == null) {
            Application.getInstance().getGUILog().log("[WARNING] " + project.getName() + " - " + ERROR_STRING + "Reason: Cannot get the server workspace that corresponds to this project branch.");
            return false;
        }
        if (ProjectUtilities.isFromEsiServer(project.getPrimaryProject())) {
            String user = EsiUtils.getLoggedUserName();
            if (user == null) {
                Application.getInstance().getGUILog().log("[ERROR] You must be logged into Teamwork Cloud. MMS sync will not start.");
                return false;
            }
        }

        JMSSyncProjectMapping jmsSyncProjectMapping = getProjectMapping(project);
        try {
            ConnectionFactory connectionFactory = JMSUtils.createConnectionFactory(jmsInfo);
            if (connectionFactory == null) {
                Application.getInstance().getGUILog().log("[WARNING] " + project.getName() + " - " + ERROR_STRING + " Reason: Failed to create JMS connection factory.");
                return false;
            }
            String subscriberId = projectID + "-" + workspaceID + "-" + TicketUtils.getUsername(); // weblogic can't have '/' in id

            JMSMessageListener jmsMessageListener = jmsSyncProjectMapping.getJmsMessageListener();

            Connection connection = connectionFactory.createConnection();
            //((WLConnection) connection).setReconnectPolicy(JMSConstants.RECONNECT_POLICY_ALL);
            connection.setExceptionListener(jmsMessageListener);
            connection.setClientID(subscriberId);
            Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            Topic topic = null;
            try {
                if (JMSUtils.getInitialContext() != null) {
                    topic = (Topic) JMSUtils.getInitialContext().lookup(JMSUtils.JMS_TOPIC);
                }
            } catch (NameNotFoundException ignored) {
                // do nothing; just means topic hasn't been created yet
            }
            if (topic == null) {
                topic = session.createTopic(JMSUtils.JMS_TOPIC);
            }
            String messageSelector = JMSUtils.constructSelectorString(projectID, workspaceID);
            MessageConsumer consumer = session.createDurableSubscriber(topic, subscriberId, messageSelector, true);
            consumer.setMessageListener(jmsMessageListener);

            MessageProducer producer = session.createProducer(topic);

            connection.start();

            jmsSyncProjectMapping.setConnection(connection);
            jmsSyncProjectMapping.setSession(session);
            jmsSyncProjectMapping.setMessageConsumer(consumer);
            jmsSyncProjectMapping.setMessageProducer(producer);

            // get everything that's already in the queue without blocking startup
            /*new Thread() {
                @Override
                public void run() {
                    List<TextMessage> textMessages = jmsSyncProjectMapping.getAllTextMessages(false);
                    if (textMessages != null) {
                        for (TextMessage textMessage : textMessages) {
                            jmsSyncProjectMapping.getJmsMessageListener().onMessage(textMessage);
                        }
                    }
                }
            }.start();*/

            Application.getInstance().getGUILog().log("[INFO] " + project.getName() + " - MMS sync initiated.");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            jmsSyncProjectMapping.getJmsMessageListener().setDisabled(true);
            Application.getInstance().getGUILog().log("[WARNING] " + project.getName() + " - " + ERROR_STRING + " Reason: " + e.getMessage());
        }
        return false;
    }

    public static JMSSyncProjectMapping getProjectMapping(Project project) {
        JMSSyncProjectMapping JMSSyncProjectMapping = projectMappings.get(project.getPrimaryProject().getProjectID());
        if (JMSSyncProjectMapping == null) {
            projectMappings.put(project.getPrimaryProject().getProjectID(), JMSSyncProjectMapping = new JMSSyncProjectMapping(project));
        }
        return JMSSyncProjectMapping;
    }

    public static class JMSSyncProjectMapping {
        private Connection connection;
        private Session session;
        private MessageConsumer messageConsumer;
        private JMSMessageListener jmsMessageListener;
        private MessageProducer messageProducer;

        public JMSSyncProjectMapping(Project project) {
            jmsMessageListener = new JMSMessageListener(project);
        }

        public Connection getConnection() {
            return connection;
        }

        public void setConnection(Connection connection) {
            this.connection = connection;
        }

        public Session getSession() {
            return session;
        }

        public void setSession(Session session) {
            this.session = session;
        }

        public MessageConsumer getMessageConsumer() {
            return messageConsumer;
        }

        public void setMessageConsumer(MessageConsumer messageConsumer) {
            this.messageConsumer = messageConsumer;
        }

        public JMSMessageListener getJmsMessageListener() {
            return jmsMessageListener;
        }

        public MessageProducer getMessageProducer() {
            return messageProducer;
        }

        public void setMessageProducer(MessageProducer messageProducer) {
            this.messageProducer = messageProducer;
        }

        @Deprecated
        public List<TextMessage> getAllTextMessages(boolean shouldAcknowledge) {
            boolean print = MDKOptionsGroup.getMDKOptions().isLogJson();
            List<TextMessage> textMessages = new ArrayList<>();
            Message message;
            try {
                while (getMessageConsumer() != null && (message = getMessageConsumer().receive(10000)) != null) {
                    if (!(message instanceof TextMessage)) {
                        continue;
                    }
                    TextMessage textMessage = (TextMessage) message;
                    if (print) {
                        System.out.println("From MMS: " + textMessage.getText());
                    }
                    textMessages.add(textMessage);

                    if (shouldAcknowledge) {
                        try {
                            message.acknowledge();
                        } catch (JMSException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (JMSException e) {
                e.printStackTrace();
                return null;
            }
            return textMessages;
        }
    }
}
