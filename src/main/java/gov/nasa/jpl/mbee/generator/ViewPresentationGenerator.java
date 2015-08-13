package gov.nasa.jpl.mbee.generator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import gov.nasa.jpl.mbee.ems.validation.ImageValidator;
import gov.nasa.jpl.mbee.lib.Utils;
import gov.nasa.jpl.mbee.model.DocBookOutputVisitor;
import gov.nasa.jpl.mbee.model.Document;
import gov.nasa.jpl.mbee.viewedit.DBAlfrescoVisitor;
import gov.nasa.jpl.mbee.viewedit.PresentationElement;
import gov.nasa.jpl.mbee.viewedit.PresentationElement.PEType;
import gov.nasa.jpl.mgss.mbee.docgen.docbook.DBBook;
import gov.nasa.jpl.mgss.mbee.docgen.validation.ValidationRule;
import gov.nasa.jpl.mgss.mbee.docgen.validation.ValidationRuleViolation;
import gov.nasa.jpl.mgss.mbee.docgen.validation.ValidationSuite;
import gov.nasa.jpl.mgss.mbee.docgen.validation.ViolationSeverity;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.ProjectUtilities;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.uml2.ext.jmi.helpers.ModelHelper;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.classes.mddependencies.Dependency;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.AggregationKind;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.AggregationKindEnum;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Association;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Classifier;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Constraint;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.ElementValue;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Expression;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.InstanceSpecification;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.InstanceValue;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.LiteralString;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Property;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Relationship;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Slot;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Type;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.TypedElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.ValueSpecification;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;
import com.nomagic.uml2.impl.ElementsFactory;
import com.nomagic.task.ProgressStatus;
import com.nomagic.task.RunnableWithProgress;

public class ViewPresentationGenerator implements RunnableWithProgress {
    private ValidationSuite suite = new ValidationSuite("View Instance Generation");
    private ValidationRule uneditableContent = new ValidationRule("Uneditable", "uneditable", ViolationSeverity.ERROR);
    private ValidationRule uneditableOwner = new ValidationRule("Uneditable owner", "uneditable owner", ViolationSeverity.WARNING);
    private ValidationRule docPackage = new ValidationRule("docPackage", "docPackage", ViolationSeverity.ERROR);
    private ValidationRule viewInProject = new ValidationRule("viewInProject", "viewInProject", ViolationSeverity.WARNING);
    private ValidationRule viewParent = new ValidationRule("viewParent", "viewParent", ViolationSeverity.WARNING);
    
    private Classifier paraC = Utils.getOpaqueParaClassifier();
    private Classifier tableC = Utils.getOpaqueTableClassifier();
    private Classifier listC = Utils.getOpaqueListClassifier();
    private Classifier imageC = Utils.getOpaqueImageClassifier();
    private Classifier sectionC = Utils.getSectionClassifier();
    private Property generatedFromView = Utils.getGeneratedFromViewProperty();
    private Property generatedFromElement = Utils.getGeneratedFromElementProperty();
    private Stereotype presentsS = Utils.getPresentsStereotype();
    private Stereotype viewS = Utils.getViewClassStereotype();
    private Stereotype productS = Utils.getProductStereotype();
    private ElementsFactory ef = Application.getInstance().getProject().getElementsFactory();
    private Package viewInstancesPackage = null;
    private Package unusedPackage = null;

    private boolean recurse;
    private Element view;

    // use these prefixes then add project_id to form the view instances id and unused view id respectively
    private String viewInstPrefix = "View_Instances";
    private String unusedInstPrefix = "Unused_View_Instances";

    // this suffix is appended to the name of each particular package
    private String genericInstSuffix = " Instances";

    private boolean cancelSession = false;
    private Map<Element, Package> view2pac = new HashMap<Element, Package>();
    
    public ViewPresentationGenerator(Element view, boolean recursive) {
        this.view = view;
        this.recurse = recursive;
    }
    
    @Override
    public void run(ProgressStatus ps) {
        suite.addValidationRule(uneditableContent);
        suite.addValidationRule(uneditableOwner);
        suite.addValidationRule(docPackage);
        suite.addValidationRule(viewInProject);
        suite.addValidationRule(viewParent);
        
        DocumentValidator dv = new DocumentValidator(view);
        dv.validateDocument();
        if (dv.isFatal()) {
            dv.printErrors(false);
            return;
        }
        // first run a local generation of the view model to get the current model view structure
        DocumentGenerator dg = new DocumentGenerator(view, dv, null);
        Document dge = dg.parseDocument(true, recurse, false);
        (new PostProcessor()).process(dge);

        DocBookOutputVisitor visitor = new DocBookOutputVisitor(true);
        DBAlfrescoVisitor visitor2 = new DBAlfrescoVisitor(recurse, true);
        dge.accept(visitor);
        DBBook book = visitor.getBook();
        if (book == null)
            return;
        book.accept(visitor2);
        Map<Element, List<PresentationElement>> view2pe = visitor2.getView2Pe();
        Map<Element, List<PresentationElement>> view2unused = visitor2.getView2Unused();
        Map<Element, JSONArray> view2elements = visitor2.getView2Elements();
        // this initializes and checks if both reserved packages are editable
        viewInstancesPackage = createViewInstancesPackage();
        unusedPackage = createUnusedInstancesPackage();

        SessionManager.getInstance().createSession("view instance gen");
        try {
            viewInstanceBuilder(view2pe, view2unused);
            for (Element v : view2elements.keySet()) {
                JSONArray es = view2elements.get(v);
                if (v.isEditable())
                    StereotypesHelper.setStereotypePropertyValue(v, Utils.getViewClassStereotype(), "elements", es.toJSONString());
            }
            if (cancelSession) {
                SessionManager.getInstance().cancelSession();
                Utils.guilog("[ERROR] View Generation canceled because some elements that require updates are not editable. See validation window.");
            } else {
                SessionManager.getInstance().closeSession();
                Utils.guilog("[INFO] View Generation completed.");
            }
        } catch (Exception e) {
            Utils.printException(e);
            SessionManager.getInstance().cancelSession();
        }

        ImageValidator iv = new ImageValidator(visitor2.getImages());
        // this checks images generated from the local generation against what's on the web based on checksum
        iv.validate();
        if (!iv.getRule().getViolations().isEmpty() || suite.hasErrors()) {
            ValidationSuite imageSuite = iv.getSuite();
            List<ValidationSuite> vss = new ArrayList<ValidationSuite>();
            vss.add(imageSuite);
            vss.add(suite);
            Utils.displayValidationWindow(vss, "View Generation and Images Validation");
        }
    }

    private void viewInstanceBuilder( Map<Element, List<PresentationElement>> view2pe, Map<Element, List<PresentationElement>> view2unused) {
        // first pass through all the views and presentation elements to handle them
        Set<Element> skippedViews = new HashSet<Element>();
        for (Element v : view2pe.keySet()) {
            // only worry about the views in the current module, output to log if they aren't there
            if (!ProjectUtilities.isElementInAttachedProject(v)) {
                handleViewOrSection(v, null, view2pe.get(v));
            } else {
                ValidationRuleViolation violation = new ValidationRuleViolation(v, "[IN MODULE] This view is in a module and was not processed.");
                viewInProject.addViolation(violation);
                skippedViews.add(v);
                //Application.getInstance().getGUILog().log("[INFO] View " + view.getID() + " not in current project.");
            }
        }
        if (cancelSession)
            return;
        //by now all views should have their instance package available
        setPackageHierarchy(skippedViews);
        // then, pass through all the unused PresentationElements and move their particular InstanceSpecification to the unused InstSpec package
        for (List<PresentationElement> presElems : view2unused.values()) {
            // only can worry about the presElems in the current project
            for (PresentationElement presentationElement : presElems) {
                // but we only really care about these instances, since that's all that we can ask about
                InstanceSpecification is = presentationElement.getInstance();
                if (!ProjectUtilities.isElementInAttachedProject(is) && is.isEditable()) {
                    is.setOwner(unusedPackage);
                }
            }
        }
    }

    public void handleViewOrSection(Element view, InstanceSpecification section, List<PresentationElement> pes) {
        // check for manual instances (thought that was in dependencies)
        Package owner = getViewTargetPackage(view, true); 
        view2pac.put(view, owner);
        List<InstanceValue> list = new ArrayList<InstanceValue>();
        boolean created = false;
        for (PresentationElement pe : pes) {
            if (pe.isManual()) {
                InstanceValue iv = ef.createInstanceValueInstance();
                InstanceSpecification inst = pe.getInstance();
                iv.setInstance(inst);
                list.add(iv);
                // lets do some testing on the instance owner
                Element instOwner = inst.getOwner();
                boolean touchMe = true;
                for (Relationship r : instOwner.get_relationshipOfRelatedElement()) {
                    if (r instanceof Dependency && StereotypesHelper.hasStereotype(r, presentsS)) {
                        // we ignore inst and leave the owner untouched if owner
                        // has a presents stereotype
                        touchMe = false;
                        break;
                    }
                }
                // if the owner doesn't have the presents stereotype, it resets
                // the owner to the correct one
                if (touchMe) {
                    if (inst.isEditable())
                        inst.setOwner(owner);
                    else {
                        ValidationRuleViolation vrv = new ValidationRuleViolation(inst, "[NOT EDITABLE (OWNER)] This instance cannot be moved into a view instance package.");
                        uneditableOwner.addViolation(vrv);
                    }
                }
                continue;
            }
            InstanceSpecification is = pe.getInstance();
            if (is == null) {
                created = true;
                is = ef.createInstanceSpecificationInstance();

                is.setName(pe.getName());
                if (pe.getType() == PEType.PARA)
                    is.getClassifier().add(paraC);
                else if (pe.getType() == PEType.TABLE)
                    is.getClassifier().add(tableC);
                else if (pe.getType() == PEType.LIST)
                    is.getClassifier().add(listC);
                else if (pe.getType() == PEType.IMAGE)
                    is.getClassifier().add(imageC);
                else if (pe.getType() == PEType.SECTION)
                    is.getClassifier().add(sectionC);
                Slot s = ef.createSlotInstance();
                s.setOwner(is);
                s.setDefiningFeature(generatedFromView);
                ElementValue ev = ef.createElementValueInstance();
                ev.setElement(view);
                s.getValue().add(ev);
                if (pe.getType() == PEType.SECTION && pe.getLoopElement() != null) {
                    Slot ss = ef.createSlotInstance();
                    ss.setOwner(is);
                    ss.setDefiningFeature(generatedFromElement);
                    ElementValue ev2 = ef.createElementValueInstance();
                    ev2.setElement(pe.getLoopElement());
                    ss.getValue().add(ev2);
                }
            }
            if (is.isEditable()) {
                is.setOwner(owner);
                if (pe.getNewspec() != null) {
                    LiteralString ls = ef.createLiteralStringInstance();
                    ls.setOwner(is);
                    ls.setValue(pe.getNewspec().toJSONString());
                    is.setSpecification(ls);
                }
                is.setName(pe.getName());
                if (is.getName() == null || is.getName().isEmpty()) {
                    is.setName("<>");
                }
            } else {
                //check if this really needs to be edited
                boolean needEdit = false;
                ValueSpecification oldvs = is.getSpecification();
                if (pe.getNewspec() != null && !pe.getNewspec().get("type").equals("Section")) {
                    if (oldvs instanceof LiteralString && ((LiteralString)oldvs).getValue() != null) {
                        JSONObject oldob = (JSONObject)JSONValue.parse(((LiteralString)oldvs).getValue());
                        if (!oldob.equals(pe.getNewspec()))
                            needEdit = true;
                    } else
                        needEdit = true;
                }
                if (is.getOwner() != owner) {
                    ValidationRuleViolation vrv = new ValidationRuleViolation(is, "[NOT EDITABLE (OWNER)] This presentation element instance can't be moved to the right view instance package.");
                    uneditableOwner.addViolation(vrv);
                }
                if (needEdit) {
                    ValidationRuleViolation vrv = new ValidationRuleViolation(is, "[NOT EDITABLE (CONTENT)] This presentation element instance can't be updated.");
                    uneditableContent.addViolation(vrv);
                    cancelSession = true;
                }
            }
            InstanceValue iv = ef.createInstanceValueInstance();
            iv.setInstance(is);
            list.add(iv);
            if (pe.getType() == PEType.SECTION) {
                handleViewOrSection(view, is, pe.getChildren());
            }
            pe.setInstance(is);
        }
        if (section != null) {
            if (!section.isEditable()) {
                boolean needEdit = false;
                if (section.getSpecification() != null && section.getSpecification() instanceof Expression) {
                    List<ValueSpecification> model = ((Expression)section.getSpecification()).getOperand();
                    if (model.size() != list.size())
                        needEdit = true;
                    else {
                        for (int i = 0; i < model.size(); i++) {
                            ValueSpecification modelvs = model.get(i);
                            if (!(modelvs instanceof InstanceValue) || ((InstanceValue)modelvs).getInstance() != list.get(i).getInstance()) {
                                needEdit = true;
                                break;
                            }
                        }
                    }
                } else
                    needEdit = true;
                if (needEdit) {
                    ValidationRuleViolation vrv = new ValidationRuleViolation(section, "[NOT EDITABLE (SECTION CONTENT)] This section instance can't be updated.");
                    uneditableContent.addViolation(vrv);
                    if (created)
                        cancelSession = true;
                }
            } else {
                Expression ex = ef.createExpressionInstance();
                ex.setOwner(section);
                ex.getOperand().addAll(list);
                section.setSpecification(ex);
            }
        } else {
            Constraint c = getViewConstraint(view);
            if (!c.isEditable()) {
                boolean needEdit = false;
                if (c.getSpecification() != null && c.getSpecification() instanceof Expression) {
                    List<ValueSpecification> model = ((Expression)c.getSpecification()).getOperand();
                    if (model.size() != list.size())
                        needEdit = true;
                    else {
                        for (int i = 0; i < model.size(); i++) {
                            ValueSpecification modelvs = model.get(i);
                            if (!(modelvs instanceof InstanceValue) || ((InstanceValue)modelvs).getInstance() != list.get(i).getInstance()) {
                                needEdit = true;
                                break;
                            }
                        }
                    }
                } else
                    needEdit = true;
                if (needEdit) {
                    ValidationRuleViolation vrv = new ValidationRuleViolation(c, "[NOT EDITABLE (VIEW CONTENT)] This view constraint can't be updated.");
                    uneditableContent.addViolation(vrv);
                    if (created)
                        cancelSession = true;
                }
            } else {
                Expression ex = ef.createExpressionInstance();
                ex.setOwner(c);
                ex.getOperand().addAll(list);
                c.setSpecification(ex);
            }
        }
    }

    //get or create view constraint
    private Constraint getViewConstraint(Element view) {
        Constraint c = Utils.getViewConstraint(view);
        if (c != null)
            return c;
        c = ef.createConstraintInstance();
        c.setOwner(view);
        c.getConstrainedElement().add(view);
        return c;
    }

    //get or create view instance package
    private Package getViewTargetPackage(Element view, boolean create) {
        // if you can find the folder with this Utils, just go ahead and return it
        // consider setting folder id?
        List<Element> results = Utils.collectDirectedRelatedElementsByRelationshipStereotype(view, presentsS, 1, false, 1);
        if (!results.isEmpty() && results.get(0) instanceof Package) {
            final Package p = (Package) results.get(0);
            //setPackageHierarchy(view, viewInst, p);
            //p.setName(getViewTargetPackageName(view));
            return p;
        }
        if (create) {
            Package viewTarget = ef.createPackageInstance();
            String viewName = ((NamedElement)view).getName();
            viewTarget.setName(((viewName == null || viewName.isEmpty()) ? view.getID() : viewName) + " " + genericInstSuffix);
            viewTarget.setOwner(viewInstancesPackage);

            Dependency d = ef.createDependencyInstance();
            d.setOwner(viewTarget);
            ModelHelper.setSupplierElement(d, viewTarget);
            ModelHelper.setClientElement(d, view);
            StereotypesHelper.addStereotype(d, presentsS);
            return viewTarget;
        }
        return null;
    }

    private void setPackageHierarchy(Set<Element> skipped) {
        for (Element view: view2pac.keySet()) {
            Type viewt = (Type)view;
            Element parentView = null;
            Package parentPack = null;
            if (StereotypesHelper.hasStereotypeOrDerived(view, productS)) {
                if (!(view.getOwner() instanceof Package)) {
                    ValidationRuleViolation vrv = new ValidationRuleViolation(view, "[DOCUMENT OWNER] A document should be owned by a package.");
                    docPackage.addViolation(vrv);
                    Element owner = view.getOwner();
                    while (!(owner instanceof Package)) {
                        owner = owner.getOwner();
                    }
                    parentPack = (Package)owner;
                } else
                    parentPack = (Package)view.getOwner();
            } else {
                for (TypedElement t: viewt.get_typedElementOfType()) {
                    if (t instanceof Property && ((Property)t).getAggregation().equals(AggregationKindEnum.COMPOSITE) &&
                            StereotypesHelper.hasStereotypeOrDerived(t.getOwner(), viewS)) {
                        if (parentView != null) {
                            ValidationRuleViolation vrv = new ValidationRuleViolation(view, "[CANONICAL PARENT] This view has multiple parent views that uses composition");
                            viewParent.addViolation(vrv);
                            break;
                        }
                        parentView = t.getOwner();
                    }
                }
            }
            if (parentPack == null) {
                if (parentView != null) {
                    if (view2pac.containsKey(parentView))
                        parentPack = view2pac.get(parentView);
                    else {
                        List<Element> results = Utils.collectDirectedRelatedElementsByRelationshipStereotype(parentView, presentsS, 1, false, 1);
                        if (!results.isEmpty() && results.get(0) instanceof Package && !ProjectUtilities.isElementInAttachedProject(results.get(0))) {
                            parentPack = (Package)results.get(0);
                        } else
                            parentPack = viewInstancesPackage;
                    }
                } else {
                    ValidationRuleViolation vrv = new ValidationRuleViolation(view, "[CANONICAL PARENT] This view has no parent view found that composes it.");
                    viewParent.addViolation(vrv);
                    parentPack = viewInstancesPackage;
                }
            }
            Package currentPack = view2pac.get(view);
            if (currentPack.getOwner() != parentPack) {
                if (currentPack.isEditable())
                    currentPack.setOwner(parentPack);
                else {
                    ValidationRuleViolation vrv = new ValidationRuleViolation(currentPack, "[NOT EDITABLE (OWNER)] View instance package cannot be moved to right hierarchy");
                    uneditableOwner.addViolation(vrv);
                }
            }
        }
    }

    private Package createViewInstancesPackage() {
        // fix root element, set it to project instead
        return createParticularPackage(Utils.getRootElement(), viewInstPrefix, "View Instances");
    }

    private Package createUnusedInstancesPackage() {
        Package rootPackage = createParticularPackage(Utils.getRootElement(), viewInstPrefix, "View Instances");
        return createParticularPackage(rootPackage, unusedInstPrefix, "Unused View Instances");
    }

    //get or create package with id 
    private Package createParticularPackage(Package owner, String packIDPrefix, String name) {
        // fix root element, set it to project replace PROJECT with the packIDPrefix
        String viewInstID = Utils.getProject().getPrimaryProject() .getProjectID().replace("PROJECT", packIDPrefix);
        Package viewInst = (Package)Application.getInstance().getProject().getElementByID(viewInstID);
        if (viewInst == null) {
            Application.getInstance().getProject().getCounter().setCanResetIDForObject(true);
            viewInst = ef.createPackageInstance();
            viewInst.setID(viewInstID);
            viewInst.setName(name);
        }
        // either way, set the owner to the owner we passed in
        if (viewInst.isEditable() && viewInst.getOwner() != owner)
            viewInst.setOwner(owner);
        else if (viewInst.getOwner() != owner) {
            //vlaidation error of trying to change owner but can't?
        }
        return viewInst;
    }

}
