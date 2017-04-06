package gov.nasa.jpl.mbee.mdk.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.core.ProjectUtilities;
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager;
import com.nomagic.magicdraw.openapi.uml.ReadOnlyElementException;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.task.ProgressStatus;
import com.nomagic.task.RunnableWithProgress;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import gov.nasa.jpl.mbee.mdk.api.incubating.MDKConstants;
import gov.nasa.jpl.mbee.mdk.api.incubating.convert.Converters;
import gov.nasa.jpl.mbee.mdk.docgen.docbook.DBBook;
import gov.nasa.jpl.mbee.mdk.emf.EMFImporter;
import gov.nasa.jpl.mbee.mdk.http.ServerException;
import gov.nasa.jpl.mbee.mdk.json.ImportException;
import gov.nasa.jpl.mbee.mdk.json.JacksonUtils;
import gov.nasa.jpl.mbee.mdk.util.Changelog;
import gov.nasa.jpl.mbee.mdk.util.MDUtils;
import gov.nasa.jpl.mbee.mdk.util.Utils;
import gov.nasa.jpl.mbee.mdk.mms.MMSUtils;
import gov.nasa.jpl.mbee.mdk.mms.json.JsonDiffFunction;
import gov.nasa.jpl.mbee.mdk.mms.json.JsonEquivalencePredicate;
import gov.nasa.jpl.mbee.mdk.mms.sync.local.LocalSyncProjectEventListenerAdapter;
import gov.nasa.jpl.mbee.mdk.mms.sync.local.LocalSyncTransactionCommitListener;
import gov.nasa.jpl.mbee.mdk.mms.sync.queue.OutputQueue;
import gov.nasa.jpl.mbee.mdk.mms.sync.queue.Request;
import gov.nasa.jpl.mbee.mdk.mms.validation.ImageValidator;
import gov.nasa.jpl.mbee.mdk.model.DocBookOutputVisitor;
import gov.nasa.jpl.mbee.mdk.model.Document;
import gov.nasa.jpl.mbee.mdk.validation.ValidationRule;
import gov.nasa.jpl.mbee.mdk.validation.ValidationRuleViolation;
import gov.nasa.jpl.mbee.mdk.validation.ValidationSuite;
import gov.nasa.jpl.mbee.mdk.validation.ViolationSeverity;
import gov.nasa.jpl.mbee.mdk.viewedit.DBAlfrescoVisitor;
import gov.nasa.jpl.mbee.mdk.viewedit.ViewHierarchyVisitor;
import gov.nasa.jpl.mbee.mdk.util.Pair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.json.simple.JSONArray;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

/**
 * @author dlam
 */

public class ViewPresentationGenerator implements RunnableWithProgress {
    private ValidationSuite suite = new ValidationSuite("View Instance Generation");
    private ValidationRule uneditableContent = new ValidationRule("Uneditable", "uneditable", ViolationSeverity.ERROR);
    private ValidationRule uneditableElements = new ValidationRule("Uneditable elements", "uneditable elements", ViolationSeverity.WARNING);
    private ValidationRule viewInProject = new ValidationRule("viewInProject", "viewInProject", ViolationSeverity.WARNING);
    private ValidationRule updateFailed = new ValidationRule("updateFailed", "updateFailed", ViolationSeverity.ERROR);
    private ValidationRule viewDoesNotExist = new ValidationRule("viewDoesNotExist", "viewDoesNotExist", ViolationSeverity.ERROR);

    private PresentationElementUtils instanceUtils;

    private boolean recurse;
    private Element start;
    private boolean failure = false;

    private Project project;
    private boolean showValidation;

    private final List<ValidationSuite> vss = new ArrayList<>();
    private final Map<String, ObjectNode> images;
    private final Set<Element> processedElements;
    private final boolean manageSesssions;

    public ViewPresentationGenerator(Element start, boolean recurse, boolean showValidation, PresentationElementUtils viu, Map<String, ObjectNode> images, Set<Element> processedElements, boolean manageSesssions) {
        this.start = start;
        this.project = Project.getProject(start);
        this.images = images != null ? images : new HashMap<>();
        this.processedElements = processedElements != null ? processedElements : new HashSet<>();
        this.recurse = recurse;
        this.showValidation = showValidation;
        this.instanceUtils = viu;
        if (this.instanceUtils == null) {
            this.instanceUtils = new PresentationElementUtils();
        }
        suite.addValidationRule(uneditableContent);
        suite.addValidationRule(viewInProject);
        suite.addValidationRule(updateFailed);
        suite.addValidationRule(uneditableElements);
        suite.addValidationRule(viewDoesNotExist);
        this.manageSesssions = manageSesssions;
        vss.add(suite);
    }

    public ViewPresentationGenerator(Element start, boolean recurse, boolean showValidation, PresentationElementUtils viu, Map<String, ObjectNode> images, Set<Element> processedElements) {
        this(start, recurse, showValidation, viu, images, processedElements, true);
    }

        @Override
    public void run(ProgressStatus progressStatus) {
        progressStatus.init("Initializing", 6);
        // Ensure no existing session so we have full control of whether to close/cancel further sessions.
        // no wild sessions spotted as of 06/05/16
        if (manageSesssions) {
            if (SessionManager.getInstance().isSessionCreated(project)) {
                SessionManager.getInstance().closeSession(project);
            }
        }

        // STAGE 1: Calculating view structure
        progressStatus.setDescription("Calculating view structure");
        progressStatus.setCurrent(1);

        DocumentValidator dv = new DocumentValidator(start);
        dv.validateDocument();
        if (dv.isFatal()) {
            dv.printErrors(false);
            return;
        }
        // first run a local generation of the view model to get the current model view structure
        DocumentGenerator dg = new DocumentGenerator(start, dv, null, false);
        Document dge = dg.parseDocument(true, recurse, false);
        new PostProcessor().process(dge);

        DocBookOutputVisitor docBookOutputVisitor = new DocBookOutputVisitor(true);
        dge.accept(docBookOutputVisitor);
        DBBook book = docBookOutputVisitor.getBook();
        // TODO ??
        if (book == null) {
            return;
        }
        // Use HierarchyVisitor to find all views to download related elements (instances, constraint, etc.)
        ViewHierarchyVisitor viewHierarchyVisitor = new ViewHierarchyVisitor();
        dge.accept(viewHierarchyVisitor);

        Map<String, Pair<ObjectNode, InstanceSpecification>> instanceSpecificationMap = new LinkedHashMap<>();
        Map<String, Pair<ObjectNode, Slot>> slotMap = new LinkedHashMap<>();
        Map<String, ViewMapping> viewMap = new LinkedHashMap<>(viewHierarchyVisitor.getView2ViewElements().size());

        for (Element view : viewHierarchyVisitor.getView2ViewElements().keySet()) {
            if (processedElements.contains(view)) {
                Application.getInstance().getGUILog().log("Detected duplicate view reference. Skipping generation for " + Converters.getElementToIdConverter().apply(view) + ".");
                continue;
            }
            ViewMapping viewMapping = viewMap.containsKey(Converters.getElementToIdConverter().apply(view)) ?
                    viewMap.get(Converters.getElementToIdConverter().apply(view)) : new ViewMapping();
            viewMapping.setElement(view);
            viewMap.put(Converters.getElementToIdConverter().apply(view), viewMapping);
        }

        // Find and delete existing view constraints to prevent ID conflict when importing. Migration should handle this,
        // but best to not let the user corrupt their model. Have also noticed an MD bug where the constraint just sticks around
        // after a session cancellation.
        List<Constraint> constraintsToBeDeleted = new ArrayList<>(viewMap.size());
        for (ViewMapping viewMapping : viewMap.values()) {
            Element view = viewMapping.getElement();
            if (view == null) {
                continue;
            }
            Constraint constraint = Utils.getViewConstraint(view);
            if (constraint != null) {
                constraintsToBeDeleted.add(constraint);
            }
        }

        if (!constraintsToBeDeleted.isEmpty()) {
            if (manageSesssions) {
                SessionManager.getInstance().createSession(project, "Legacy View Constraint Purge");
            }
            for (Constraint constraint : constraintsToBeDeleted) {
                if (constraint.isEditable()) {
                    Application.getInstance().getGUILog().log("Deleting legacy view constraint: " + Converters.getElementToIdConverter().apply(constraint));
                    try {
                        ModelElementsManager.getInstance().removeElement(constraint);
                    } catch (ReadOnlyElementException e) {
                        updateFailed.addViolation(new ValidationRuleViolation(constraint, "[UPDATE FAILED] This view constraint could not be deleted automatically and needs to be deleted to prevent ID conflicts."));
                        failure = true;
                    }
                }
                else {
                    updateFailed.addViolation(new ValidationRuleViolation(constraint, "[UPDATE FAILED] This view constraint could not be deleted automatically and needs to be deleted to prevent ID conflicts."));
                    failure = true;
                }
            }
            if (manageSesssions) {
                SessionManager.getInstance().closeSession(project);
            }
        }

        if (failure) {
            if (showValidation) {
                Utils.displayValidationWindow(project, vss, "View Generation Validation");
            }
            return;
        }

        // Allowing cancellation right before potentially long server queries
        if (handleCancel(progressStatus)) {
            return;
        }

        LocalSyncTransactionCommitListener localSyncTransactionCommitListener = LocalSyncProjectEventListenerAdapter.getProjectMapping(project).getLocalSyncTransactionCommitListener();

        // Create the session you intend to cancel to revert all temporary elements.
        if (manageSesssions) {
            if (SessionManager.getInstance().isSessionCreated(project)) {
                SessionManager.getInstance().closeSession(project);
            }
            SessionManager.getInstance().createSession(project, "View Presentation Generation - Cancelled");
        }
        if (localSyncTransactionCommitListener != null) {
            localSyncTransactionCommitListener.setDisabled(true);
        }

        // Query existing server-side JSONs for views
        List<String> viewIDs = new ArrayList<>();
        if (!viewMap.isEmpty()) {
            // STAGE 2: Downloading existing view instances
            progressStatus.setDescription("Downloading existing view instances");
            progressStatus.setCurrent(2);

            ObjectNode viewResponse;
            try {
                viewResponse = JacksonUtils.parseJsonObject(MMSUtils.getElements(project, viewMap.keySet(), progressStatus));
            } catch (ServerException | IOException | URISyntaxException e) {
                failure = true;
                Application.getInstance().getGUILog().log("[WARNING] Server error occurred. Please check your network connection or view logs for more information.");
                e.printStackTrace();
                return;
            }
            JsonNode viewElementsJsonArray;
            if (viewResponse != null && (viewElementsJsonArray = viewResponse.get("elements")) != null && viewElementsJsonArray.isArray()) {
                Queue<String> instanceIDs = new LinkedList<>();
                Queue<String> slotIDs = new LinkedList<>();
                Property generatedFromViewProperty = Utils.getGeneratedFromViewProperty(project),
                        generatedFromElementProperty = Utils.getGeneratedFromElementProperty(project);
                for (JsonNode elementJsonNode : viewElementsJsonArray) {
                    if (!elementJsonNode.isObject()) {
                        continue;
                    }
                    ObjectNode elementObjectNode = (ObjectNode) elementJsonNode;
                    // Resolve current instances in the view constraint expression
                    JsonNode viewOperandJsonNode = JacksonUtils.getAtPath(elementObjectNode, "/" + MDKConstants.CONTENTS_KEY + "/operand"),
                            sysmlIdJson = elementObjectNode.get(MDKConstants.ID_KEY);
                    String sysmlId;
                    if (sysmlIdJson != null && sysmlIdJson.isTextual() && !(sysmlId = sysmlIdJson.asText()).isEmpty()) {
                        viewIDs.add(sysmlId);
                        if (viewOperandJsonNode != null && viewOperandJsonNode.isArray()) {
                            // store returned ids so we can exclude view generation for elements that aren't on the mms yet

                            List<String> viewInstanceIDs = new ArrayList<>(viewOperandJsonNode.size());
                            for (JsonNode viewOperandJson : viewOperandJsonNode) {
                                JsonNode instanceIdJsonNode = viewOperandJson.get(MDKConstants.INSTANCE_ID_KEY);
                                String instanceId;
                                if (instanceIdJsonNode != null && instanceIdJsonNode.isTextual() && !(instanceId = instanceIdJsonNode.asText()).isEmpty()) {
                                /*if (!instanceID.endsWith(PresentationElementUtils.ID_KEY_SUFFIX)) {
                                    continue;
                                }*/
                                    if (generatedFromViewProperty != null) {
                                        slotIDs.add(instanceId + MDKConstants.SLOT_ID_SEPARATOR + Converters.getElementToIdConverter().apply(generatedFromViewProperty));
                                    }
                                    if (generatedFromElementProperty != null) {
                                        slotIDs.add(instanceId + MDKConstants.SLOT_ID_SEPARATOR + Converters.getElementToIdConverter().apply(generatedFromElementProperty));
                                    }
                                    instanceIDs.add(instanceId);
                                    viewInstanceIDs.add(instanceId);
                                }
                            }
                            ViewMapping viewMapping = viewMap.containsKey(sysmlId) ? viewMap.get(sysmlId) : new ViewMapping();
                            viewMapping.setObjectNode(elementObjectNode);
                            viewMapping.setInstanceIDs(viewInstanceIDs);
                            viewMap.put(sysmlId, viewMapping);
                        }
                    }
                }

                // Now that all first-level instances are resolved, query for them and import client-side (in reverse order) as model elements
                // Add any sections that are found along the way and loop until no more are found
                List<ObjectNode> instanceObjectNodes = new ArrayList<>();
                List<ObjectNode> slotObjectNodes = new ArrayList<>();
                while (!instanceIDs.isEmpty() && !slotIDs.isEmpty()) {
                    // Allow cancellation between every depths' server query.
                    if (handleCancel(progressStatus)) {
                        return;
                    }

                    List<String> elementIDs = new ArrayList<>(instanceIDs);
                    elementIDs.addAll(slotIDs);

                    ObjectNode instanceAndSlotResponse;
                    try {
                        instanceAndSlotResponse = JacksonUtils.parseJsonObject(MMSUtils.getElements(project, elementIDs, progressStatus));
                    } catch (ServerException | IOException | URISyntaxException e) {
                        failure = true;
                        Application.getInstance().getGUILog().log("[WARNING] Server error occurred. Please check your network connection or view logs for more information.");
                        e.printStackTrace();
                        if (manageSesssions) {
                            SessionManager.getInstance().cancelSession(project);
                        }
                        return;
                    }
                    instanceIDs.clear();
                    slotIDs.clear();
                    JsonNode instanceAndSlotElementsJsonArray;
                    if (instanceAndSlotResponse != null && (instanceAndSlotElementsJsonArray = instanceAndSlotResponse.get("elements")) != null && instanceAndSlotElementsJsonArray.isArray()) {
                        for (JsonNode elementJson : instanceAndSlotElementsJsonArray) {
                            JsonNode instanceOperandJsonArray = JacksonUtils.getAtPath(elementJson, "/specification/operand");
                            if (instanceOperandJsonArray != null && instanceOperandJsonArray.isArray()) {
                                for (JsonNode instanceOperandJson : instanceOperandJsonArray) {
                                    JsonNode instanceIdJson = instanceOperandJson.get(MDKConstants.INSTANCE_ID_KEY);
                                    String instanceId;
                                    if (instanceIdJson != null && instanceIdJson.isTextual() && !(instanceId = instanceIdJson.asText()).isEmpty()) {
                                        /*if (!instanceID.endsWith(PresentationElementUtils.ID_KEY_SUFFIX)) {
                                            continue;
                                        }*/
                                        if (generatedFromViewProperty != null) {
                                            slotIDs.add(instanceId + MDKConstants.SLOT_ID_SEPARATOR + Converters.getElementToIdConverter().apply(generatedFromViewProperty));
                                        }
                                        if (generatedFromElementProperty != null) {
                                            slotIDs.add(instanceId + MDKConstants.SLOT_ID_SEPARATOR + Converters.getElementToIdConverter().apply(generatedFromElementProperty));
                                        }
                                        instanceIDs.add(instanceId);
                                    }
                                }
                            }
                            JsonNode typeJson = elementJson.get(MDKConstants.TYPE_KEY);
                            if (typeJson.isTextual() && elementJson.isObject()) {
                                (typeJson.asText().equalsIgnoreCase("slot") ? slotObjectNodes : instanceObjectNodes).add((ObjectNode) elementJson);
                            }
                        }
                    }
                }

                // STAGE 3: Importing existing view instances
                progressStatus.setDescription("Importing existing view instances");
                progressStatus.setCurrent(3);

                EMFImporter emfImporter = new EMFImporter() {
                    @Override
                    public List<PreProcessor> getPreProcessors() {
                        if (preProcessors == null) {
                            preProcessors = new ArrayList<>(super.getPreProcessors());
                            preProcessors.remove(PreProcessor.SYSML_ID_VALIDATION);
                        }
                        return preProcessors;
                    }

                    @Override
                    public List<EStructuralFeatureOverride> getEStructuralFeatureOverrides() {
                        if (eStructuralFeatureOverrides == null) {
                            eStructuralFeatureOverrides = new ArrayList<>(super.getEStructuralFeatureOverrides());
                            eStructuralFeatureOverrides.remove(EStructuralFeatureOverride.OWNER);
                            eStructuralFeatureOverrides.add(new EStructuralFeatureOverride(
                                    EStructuralFeatureOverride.OWNER.getPredicate(),
                                    (objectNode, eStructuralFeature, project, strict, element) -> {
                                        if (element instanceof InstanceSpecification) {
                                            element.setOwner(project.getPrimaryModel());
                                            return element;
                                        }
                                        return EStructuralFeatureOverride.OWNER.getFunction().apply(objectNode, eStructuralFeature, project, strict, element);
                                    }));
                        }
                        return eStructuralFeatureOverrides;
                    }
                };

                for (Boolean strict : Arrays.asList(false, true)) {
                    // importing instances in reverse order so that deepest level instances (sections and such) are loaded first
                    ListIterator<ObjectNode> instanceObjectNodesIterator = instanceObjectNodes.listIterator(instanceObjectNodes.size());
                    while (instanceObjectNodesIterator.hasPrevious()) {
                        if (handleCancel(progressStatus)) {
                            return;
                        }

                        ObjectNode instanceObjectNode = instanceObjectNodesIterator.previous();
                        try {
                            // Slots will break if imported with owner (instance) ignored, but we need to ignore InstanceSpecification owners
                            //Element element = ImportUtility.createElement(elementJsonNode, false, true);
                            Changelog.Change<Element> change = emfImporter.apply(instanceObjectNode, project, strict);
                            Element element = change != null ? change.getChanged() : null;

                            if (element instanceof InstanceSpecification) {
                                instanceSpecificationMap.put(Converters.getElementToIdConverter().apply(element), new Pair<>(instanceObjectNode, (InstanceSpecification) element));
                            }
                        } catch (ImportException | ReadOnlyElementException e) {
                            /*failure = true;
                            Utils.printException(e);
                            if (manageSesssions) {
                                SessionManager.getInstance().cancelSession();
                            }
                            return;*/
                            Application.getInstance().getGUILog().log("[WARNING] Failed to import instance specification " + instanceObjectNode.get(MDKConstants.ID_KEY) + ": " + e.getMessage());
                            instanceObjectNodesIterator.remove();
                        }
                    }

                    // The Alfresco service is a nice guy and returns Slots at the end so that when it's loaded in order the slots don't throw errors for missing their instances.
                    // However we're being fancy and loading them backwards so we had to separate them ahead of time and then load the slots after.
                    ListIterator<ObjectNode> slotObjectNodesIterator = slotObjectNodes.listIterator();
                    while (slotObjectNodesIterator.hasNext()) {
                        if (handleCancel(progressStatus)) {
                            return;
                        }

                        ObjectNode slotObjectNode = slotObjectNodesIterator.next();
                        try {
                            // Slots will break if imported with owner (instance) ignored, but we need to ignore InstanceSpecification owners
                            //Element element = ImportUtility.createElement(slotJsonNode, false, false);
                            Changelog.Change<Element> change = emfImporter.apply(slotObjectNode, project, strict);
                            Element element = change != null ? change.getChanged() : null;

                            if (element instanceof Slot) {
                                slotMap.put(Converters.getElementToIdConverter().apply(element), new Pair<>(slotObjectNode, (Slot) element));
                            }
                        } catch (ImportException | ReadOnlyElementException e) {
                            /*failure = true;
                            Utils.printException(e);
                            if (manageSesssions) {
                                SessionManager.getInstance().cancelSession();
                            }
                            return;*/
                            Application.getInstance().getGUILog().log("[WARNING] Failed to import slot " + slotObjectNode.get(MDKConstants.ID_KEY) + ": " + e.getMessage());
                            slotObjectNodesIterator.remove();
                        }
                    }
                }

                // Build view constraints client-side as actual Constraint, Expression, InstanceValue(s), etc.
                // Note: Doing this one first since what it does is smaller in scope than ImportUtility. Potential order-dependent edge cases require further evaluation.
                for (ViewMapping viewMapping : viewMap.values()) {
                    Element view = viewMapping.getElement();
                    if (handleCancel(progressStatus)) {
                        return;
                    }

                    ObjectNode viewObjectNode = viewMapping.getObjectNode();
                    if (viewObjectNode == null) {
                        continue;
                    }
                    JsonNode viewContentsJsonNode = viewObjectNode.get(MDKConstants.CONTENTS_KEY);
                    if (viewContentsJsonNode == null || !viewContentsJsonNode.isObject()) {
                        continue;
                    }
                    try {
                        Changelog.Change<Element> change = Converters.getJsonToElementConverter().apply((ObjectNode) viewContentsJsonNode, project, false);
                        if (change.getChanged() != null && change.getChanged() instanceof Expression) {
                            instanceUtils.getOrCreateViewConstraint(view).setSpecification((Expression) change.getChanged());
                        }
                    } catch (ImportException | ReadOnlyElementException e) {
                        Application.getInstance().getGUILog().log("[WARNING] Could not create view contents for " + Converters.getElementToIdConverter().apply(view) + ". The result could be that the view contents are created from scratch.");
                        continue;
                    }
                }

                /*for (ViewMapping viewMapping : viewMap.values()) {
                    Element view = viewMapping.getElement();
                    if (handleCancel(progressStatus)) {
                        return;
                    }

                    List<String> instanceSpecificationIDs;
                    if (viewMap.containsKey(view.getID()) && (instanceSpecificationIDs = viewMap.get(view.getID()).getInstanceIDs()) != null) {
                        final List<InstanceSpecification> instanceSpecifications = new ArrayList<>(instanceSpecificationIDs.size());
                        for (String instanceSpecificationID : instanceSpecificationIDs) {
                            Pair<ObjectNode, InstanceSpecification> pair = instanceSpecificationMap.get(instanceSpecificationID);
                            if (pair != null && pair.getSecond() != null) {
                                instanceSpecifications.add(pair.getSecond());
                            }
                        }
                        instanceUtils.updateOrCreateConstraintFromInstanceSpecifications(view, instanceSpecifications);
                    }
                }*/

                // Update relations for all InstanceSpecifications and Slots
                // Instances need to be done in reverse order to load the lowest level instances first (sections)
                /*ListIterator<Pair<ObjectNode, InstanceSpecification>> instanceSpecificationMapIterator = new ArrayList<>(instanceSpecificationMap.values()).listIterator(instanceSpecificationMap.size());
                while (instanceSpecificationMapIterator.hasPrevious()) {
                    if (handleCancel(progressStatus)) {
                        return;
                    }

                    Pair<ObjectNode, InstanceSpecification> pair = instanceSpecificationMapIterator.previous();
                    try {
                        //ImportUtility.createElement(pair.getFirst(), true, true);
                        emfImporter.apply(pair.getFirst(), project, true);
                    } catch (Exception e) {
                        /*failure = true;
                        Utils.printException(e);
                        if (manageSesssions) {
                            SessionManager.getInstance().cancelSession();
                        }
                        return;* /
                        Application.getInstance().getGUILog().log("[ERROR] Failed to update relations for instance specification " + pair.getFirst().get(MDKConstants.ID_KEY) + ": " + e.getMessage());
                    }
                }
                for (Pair<ObjectNode, Slot> pair : slotMap.values()) {
                    if (handleCancel(progressStatus)) {
                        return;
                    }

                    try {
                        //ImportUtility.createElement(pair.getFirst(), true, false);
                        emfImporter.apply(pair.getFirst(), project, true);
                    } catch (Exception e) {
                        /*failure = true;
                        Utils.printException(e);
                        if (manageSesssions) {
                            SessionManager.getInstance().cancelSession();
                        }
                        return;* /
                        Application.getInstance().getGUILog().log("[ERROR] Failed to update relations for slot " + pair.getFirst().get(MDKConstants.ID_KEY) + ": " + e.getMessage());
                    }
                }*/
            }
        }

        // STAGE 4: Generating new view instances
        progressStatus.setDescription("Generating new view instances");
        progressStatus.setCurrent(4);

        DBAlfrescoVisitor dbAlfrescoVisitor = new DBAlfrescoVisitor(recurse, true);
        try {
            book.accept(dbAlfrescoVisitor);
        } catch (Exception e) {
            Utils.printException(e);
            e.printStackTrace();
        }
        Map<Element, List<PresentationElementInstance>> view2pe = dbAlfrescoVisitor.getView2Pe();
        Map<Element, List<PresentationElementInstance>> view2unused = dbAlfrescoVisitor.getView2Unused();
        List<Element> views = instanceUtils.getViewProcessOrder(start, dbAlfrescoVisitor.getHierarchyElements());
        views.removeAll(processedElements);
        Set<Element> skippedViews = new HashSet<>();

        for (Element view : views) {
            if (ProjectUtilities.isElementInAttachedProject(view)) {
                ValidationRuleViolation violation = new ValidationRuleViolation(view, "[IN MODULE] This view is in a module and was not processed.");
                viewInProject.addViolation(violation);
                skippedViews.add(view);
            }
            if (!viewIDs.contains(Converters.getElementToIdConverter().apply(view))) {
                ValidationRuleViolation violation = new ValidationRuleViolation(view, "View does not exist on MMS. Generation skipped.");
                viewDoesNotExist.addViolation(violation);
                skippedViews.add(view);
            }
        }

        if (failure) {
            if (showValidation) {
                Utils.displayValidationWindow(project, vss, "View Generation Validation");
            }
            if (manageSesssions) {
                SessionManager.getInstance().cancelSession(project);
            }
            return;
        }

        try {
            for (Element view : views) {
                if (skippedViews.contains(view)) {
                    continue;
                }
                // Using null package with intention to cancel session and delete instances to prevent model validation error.
                handlePes(view2pe.get(view), null);
                instanceUtils.updateOrCreateConstraintFromPresentationElements(view, view2pe.get(view));
            }

            if (handleCancel(progressStatus)) {
                return;
            }

            // commit to MMS
            LinkedList<ObjectNode> elementsToCommit = new LinkedList<>();
            Queue<Pair<InstanceSpecification, Element>> instanceToView = new LinkedList<>();
            Map<Element, JSONArray> view2elements = dbAlfrescoVisitor.getView2Elements();
            for (Element view : views) {
                if (skippedViews.contains(view)) {
                    continue;
                }
                // Sends the full view JSON if it doesn't exist on the server yet. If it does exist, it sends just the
                // portion of the JSON required to update the view contents.
                ObjectNode clientViewJson = Converters.getElementToJsonConverter().apply(view, project);
                if (clientViewJson == null) {
                    skippedViews.add(view);
                    continue;
                }
                if (view2elements.get(view) != null) {
                    ArrayNode displayedElements = clientViewJson.putArray(MDKConstants.DISPLAYED_ELEMENT_IDS_KEY);
                    for (Object id : view2elements.get(view)) {
                        displayedElements.add((String) id);
                    }
                }
                Object o;
                ObjectNode serverViewJson = (o = viewMap.get(Converters.getElementToIdConverter().apply(view))) != null ? ((ViewMapping) o).getObjectNode() : null;
                if (!JsonEquivalencePredicate.getInstance().test(clientViewJson, serverViewJson)) {
                    if (MDUtils.isDeveloperMode()) {
                        Application.getInstance().getGUILog().log("View diff for " + Converters.getElementToIdConverter().apply(view) + ": " + JsonDiffFunction.getInstance().apply(clientViewJson, serverViewJson).toString());
                    }
                    elementsToCommit.add(clientViewJson);
                }
                for (PresentationElementInstance presentationElementInstance : view2pe.get(view)) {
                    if (presentationElementInstance.getInstance() != null) {
                        instanceToView.add(new Pair<>(presentationElementInstance.getInstance(), view));
                    }
                }

                // No need to commit constraint as it's wrapped up into the view
                /*
                Constraint constraint = Utils.getViewConstraint(view);
                if (constraint != null) {
                    elementsJSONArray.add(Converters.getElementToJsonConverter().apply(constraint, project));
                }
                */
            }

            String viewInstanceBinId = MDKConstants.VIEW_INSTANCES_BIN_PREFIX + Converters.getIProjectToIdConverter().apply(project.getPrimaryProject());
            while (!instanceToView.isEmpty()) {
                Pair<InstanceSpecification, Element> pair = instanceToView.remove();
                InstanceSpecification instance = pair.getKey();

                List<InstanceSpecification> subInstances = instanceUtils.getCurrentInstances(instance, pair.getValue()).getAll();
                for (InstanceSpecification subInstance : subInstances) {
                    instanceToView.add(new Pair<>(subInstance, pair.getValue()));
                }

                ObjectNode clientInstanceSpecificationJson = Converters.getElementToJsonConverter().apply(instance, project);
                if (clientInstanceSpecificationJson == null) {
                    continue;
                }
                // override owner of pei to store parallel to the model
                clientInstanceSpecificationJson.put(MDKConstants.OWNER_ID_KEY, viewInstanceBinId);
                ObjectNode serverInstanceSpecificationJson = instanceSpecificationMap.containsKey(Converters.getElementToIdConverter().apply(instance)) ?
                        instanceSpecificationMap.get(Converters.getElementToIdConverter().apply(instance)).getKey() : null;
                if (!JsonEquivalencePredicate.getInstance().test(clientInstanceSpecificationJson, serverInstanceSpecificationJson)) {
                    if (MDUtils.isDeveloperMode()) {
                        Application.getInstance().getGUILog().log("View Instance diff for " + Converters.getElementToIdConverter().apply(instance) + ": " + JsonDiffFunction.getInstance().apply(clientInstanceSpecificationJson, serverInstanceSpecificationJson).toString());
                    }
                    elementsToCommit.add(clientInstanceSpecificationJson);
                }

                for (Slot slot : instance.getSlot()) {
                    ObjectNode clientSlotJson = Converters.getElementToJsonConverter().apply(slot, project);
                    if (clientSlotJson == null) {
                        continue;
                    }
                    JsonNode serverSlotJson = slotMap.containsKey(Converters.getElementToIdConverter().apply(slot)) ?
                            slotMap.get(Converters.getElementToIdConverter().apply(slot)).getKey() : null;
                    if (!JsonEquivalencePredicate.getInstance().test(clientSlotJson, serverSlotJson)) {
                        elementsToCommit.add(clientSlotJson);
                        if (MDUtils.isDeveloperMode()) {
                            Application.getInstance().getGUILog().log("Slot diff for " + Converters.getElementToIdConverter().apply(slot) + ": " + JsonDiffFunction.getInstance().apply(clientSlotJson, serverSlotJson).toString());
                        }
                    }
                }
            }

            // Last chance to cancel before sending generated views to the server. Point of no return.
            if (handleCancel(progressStatus)) {
                return;
            }

            boolean changed = false;

            if (elementsToCommit.size() > 0) {
                // STAGE 5: Queueing upload of generated view instances
                progressStatus.setDescription("Queueing upload of generated view instances");
                progressStatus.setCurrent(5);
                Application.getInstance().getGUILog().log("Updating/creating " + elementsToCommit.size() + " element" + (elementsToCommit.size() != 1 ? "s" : "") + " to generate views.");

                URIBuilder requestUri = MMSUtils.getServiceProjectsRefsElementsUri(project);
                File sendData = MMSUtils.createEntityFile(this.getClass(), ContentType.APPLICATION_JSON, elementsToCommit, MMSUtils.JsonBlobType.ELEMENT_JSON);
                OutputQueue.getInstance().offer(new Request(project, MMSUtils.HttpRequestType.POST, requestUri, sendData, ContentType.APPLICATION_JSON, true, elementsToCommit.size(), "Sync Changes"));
                changed = true;
            }

            // Delete unused presentation elements

            Set<String> mmsElementsToDelete = new HashSet<>();
            for (List<PresentationElementInstance> presentationElementInstances : view2unused.values()) {
                for (PresentationElementInstance presentationElementInstance : presentationElementInstances) {
                    if (presentationElementInstance.getInstance() == null) {
                        continue;
                    }
                    String id = Converters.getElementToIdConverter().apply(presentationElementInstance.getInstance());
                    if (id == null) {
                        continue;
                    }
                    mmsElementsToDelete.add(id);
                }
            }
            if (elementsToCommit.size() > 0) {
                Application.getInstance().getGUILog().log("Deleting " + mmsElementsToDelete.size() + " unused presentation element" + (mmsElementsToDelete.size() != 1 ? "s" : "") + ".");

                URIBuilder requestUri = MMSUtils.getServiceProjectsRefsElementsUri(project);
                File sendData = MMSUtils.createEntityFile(this.getClass(), ContentType.APPLICATION_JSON, mmsElementsToDelete, MMSUtils.JsonBlobType.ELEMENT_ID);
                OutputQueue.getInstance().offer(new Request(project, MMSUtils.HttpRequestType.DELETE, requestUri, sendData, ContentType.APPLICATION_JSON, true, elementsToCommit.size(), "View Generation"));
                changed = true;
            }

            if (!changed) {
                Application.getInstance().getGUILog().log("No changes required to generate views.");
            }

            // STAGE 6: Finishing up

            progressStatus.setDescription("Finishing up");
            progressStatus.setCurrent(6);

            // Cleaning up after myself. While cancelSession *should* undo all elements created, there are certain edge
            // cases like the underlying constraint not existing in the containment tree, but leaving a stale constraint
            // on the view block.
            Set<Element> elementsToDelete = new HashSet<>();
            for (Pair<ObjectNode, Slot> pair : slotMap.values()) {
                if (pair.getValue() != null) {
                    elementsToDelete.add(pair.getValue());
                }
            }
            for (Pair<ObjectNode, InstanceSpecification> pair : instanceSpecificationMap.values()) {
                if (pair.getValue() != null) {
                    elementsToDelete.add(pair.getValue());
                }
            }
            for (Element element : views) {
                Constraint constraint = Utils.getViewConstraint(element);
                if (constraint == null) {
                    continue;
                }
                elementsToDelete.add(constraint);
                ValueSpecification valueSpecification = constraint.getSpecification();
                if (valueSpecification == null) {
                    continue;
                }
                elementsToDelete.add(valueSpecification);
                List<ValueSpecification> operands;
                if (!(valueSpecification instanceof Expression) || (operands = ((Expression) valueSpecification).getOperand()) == null) {
                    continue;
                }
                for (ValueSpecification operand : operands) {
                    elementsToDelete.add(operand);
                    InstanceSpecification instanceSpecification;
                    if (!(operand instanceof InstanceValue) || (instanceSpecification = ((InstanceValue) operand).getInstance()) == null) {
                        continue;
                    }
                    elementsToDelete.add(instanceSpecification);
                    for (Slot slot : instanceSpecification.getSlot()) {
                        elementsToDelete.add(slot);
                        elementsToDelete.addAll(slot.getValue());
                    }
                }
            }
            for (Element element : elementsToDelete) {
                try {
                    ModelElementsManager.getInstance().removeElement(element);
                } catch (ReadOnlyElementException ignored) {
                    System.out.println("Could not clean up " + element.getLocalID());
                }
            }
            // used to skip redundant view generation attempts when using multi-select or ElementGroups; see GenerateViewPresentationAction
            processedElements.addAll(views);
        } catch (Exception e) {
            failure = true;
            Utils.printException(e);
        } finally {
            // cancel session so all elements created get deleted automatically
            if (manageSesssions) {
                if (SessionManager.getInstance().isSessionCreated(project)) {
                    SessionManager.getInstance().cancelSession(project);
                }
            }
            if (localSyncTransactionCommitListener != null) {
                localSyncTransactionCommitListener.setDisabled(false);
            }
        }
        ImageValidator iv = new ImageValidator(dbAlfrescoVisitor.getImages(), images);
        // this checks images generated from the local generation against what's on the web based on checksum
        iv.validate(project);
        // Auto-validate - https://cae-jira.jpl.nasa.gov/browse/MAGICDRAW-45
        for (ValidationRule validationRule : iv.getSuite().getValidationRules()) {
            for (ValidationRuleViolation validationRuleViolation : validationRule.getViolations()) {
                if (!validationRuleViolation.getActions().isEmpty()) {
                    validationRuleViolation.getActions().get(0).actionPerformed(null);
                }
            }
        }
//        vss.add(iv.getSuite());
        if (showValidation) {
            if (suite.hasErrors()) {
                Utils.displayValidationWindow(project, vss, "View Generation Validation");
            }
        }
    }

    private void handlePes(List<PresentationElementInstance> pes, Package p) {
        for (PresentationElementInstance pe : pes) {
            if (pe.getChildren() != null && !pe.getChildren().isEmpty()) {
                handlePes(pe.getChildren(), p);
            }
            instanceUtils.updateOrCreateInstance(pe, p);
        }
    }

    private boolean handleCancel(ProgressStatus progressStatus) {
        if (progressStatus.isCancel()) {
            failure = true;
            if (manageSesssions) {
                if (SessionManager.getInstance().isSessionCreated(project)) {
                    SessionManager.getInstance().cancelSession(project);
                }
            }
            Application.getInstance().getGUILog().log("View generation cancelled.");
            return true;
        }
        return false;
    }

    public List<ValidationSuite> getValidations() {
        return vss;
    }

    public boolean isFailure() {
        return failure;
    }

    private class ViewMapping {
        private Element element;
        private ObjectNode objectNode;
        private List<String> instanceIDs;

        public Element getElement() {
            return element;
        }

        public void setElement(Element element) {
            this.element = element;
        }

        public ObjectNode getObjectNode() {
            return objectNode;
        }

        public void setObjectNode(ObjectNode objectNode) {
            this.objectNode = objectNode;
        }

        public List<String> getInstanceIDs() {
            return instanceIDs;
        }

        public void setInstanceIDs(List<String> instanceIDs) {
            this.instanceIDs = instanceIDs;
        }
    }

}
