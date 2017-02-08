/*******************************************************************************
 * Copyright (c) <2016>, California Institute of Technology ("Caltech").  
 * U.S. Government sponsorship acknowledged.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are 
 * permitted provided that the following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice, this list of 
 *    conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice, this list 
 *    of conditions and the following disclaimer in the documentation and/or other materials 
 *    provided with the distribution.
 *  - Neither the name of Caltech nor its operating division, the Jet Propulsion Laboratory, 
 *    nor the names of its contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS 
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY 
 * AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER  
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON 
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/

package gov.nasa.jpl.mbee.mdk.api;

import com.nomagic.magicdraw.copypaste.CopyPasting;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.core.project.ProjectDescriptor;
import com.nomagic.magicdraw.core.project.ProjectDescriptorsFactory;
import com.nomagic.magicdraw.core.project.ProjectsManager;
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager;
import com.nomagic.magicdraw.openapi.uml.ReadOnlyElementException;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.uml2.ext.jmi.helpers.ModelHelper;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.classes.mddependencies.Dependency;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Class;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import com.nomagic.uml2.ext.magicdraw.components.mdbasiccomponents.Component;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;
import com.nomagic.uml2.impl.ElementsFactory;
import gov.nasa.jpl.mbee.mdk.ems.ReferenceException;
import gov.nasa.jpl.mbee.mdk.lib.Utils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class has functions that uses the MDK api to test the MDK actions.
 */
public class MagicDrawHelper {

    private static Project project;
    private static ElementsFactory ef;

    /**
     * Convenience method to ensure that we always have an ElementsFactory available.
     */
    private static void initializeFactory() {
        if (ef != null) {
            return;
        }
        project = Application.getInstance().getProject();
        ef = project.getElementsFactory();
    }

    /*****************************************************************************************
     *
     * Project load / close functions
     *
     *****************************************************************************************/

    public static ProjectDescriptor openProject(File file) throws IOException {
        final ProjectDescriptor projectDescriptor = ProjectDescriptorsFactory.createProjectDescriptor(file.toURI());
        if (projectDescriptor == null) {
            throw new IOException(Paths.get(file.toURI()).toString() + " could not generate a project descriptor.");
        }
        final ProjectsManager projectsManager = Application.getInstance().getProjectsManager();
        projectsManager.loadProject(projectDescriptor, true);
        final Project project = projectsManager.getActiveProject();
        if (project == null) {
            throw new IOException(Paths.get(file.toURI()).toString() + " could not be loaded into MagicDraw.");
        }
        return projectDescriptor;
    }

    public static void saveProject(String filename) {
        System.out.println(System.currentTimeMillis());
        File file = new File(filename);
        ProjectDescriptor projectDescriptor =
                ProjectDescriptorsFactory.createLocalProjectDescriptor(Application.getInstance().getProject(), file);
        Application.getInstance().getProjectsManager().saveProject(projectDescriptor, false);
        System.out.println(System.currentTimeMillis());
    }

    public static void closeProject() {
        Application.getInstance().getProjectsManager().closeProject();
    }



    /*****************************************************************************************
     *
     * Session management functions
     *
     *****************************************************************************************/

    /**
     * Creates a MagicDraw Session. All changes to be recorded in model programmatically
     * must occur after a session is opened, and will be recorded when the session is closed.
     * A cancelled session will cause the changes to be lost.
     *
     * @throws IllegalStateException
     */
    public static void createSession() throws IllegalStateException {
        if (SessionManager.getInstance().isSessionCreated()) {
            throw new IllegalStateException("Unable to create session: a session is already open.");
        }
        SessionManager.getInstance().createSession("Programmatic changes");
        initializeFactory();
    }

    /**
     * Closes an open session, causing all programmatically completed changes in the current
     * session to be reflected in the model.
     *
     * @throws IllegalStateException
     */
    public static void closeSession() throws IllegalStateException {
        if (!SessionManager.getInstance().isSessionCreated()) {
            throw new IllegalStateException("Unable to close session: no session has been created to close.");
        }
        SessionManager.getInstance().closeSession();
    }

    /**
     * Cancels an open session, causing all programmatically completed changes in the current
     * session to be lost and not recorded in the model.
     *
     * @throws IllegalStateException
     */
    public static void cancelSession() throws IllegalStateException {
        if (!SessionManager.getInstance().isSessionCreated()) {
            throw new IllegalStateException("Unable to cancel session: no session has been created to cancel.");
        }
        SessionManager.getInstance().cancelSession();
    }

    /*****************************************************************************************
     *
     * Logging functions
     *
     *****************************************************************************************/

    /**
     * Prints a message to console and MD log
     */
    public static void generalMessage(String s) {
        Application instance = Application.getInstance();
        instance.getGUILog().log(s);
        System.out.println(s);
    }

    /*****************************************************************************************
     *
     * Element positioning functions
     *
     *****************************************************************************************/

    /**
     * Copies specific elements to a location
     *
     * @param elementToCopy
     * @param copyTarget
     */
    public static Element copyElementToTarget(Element elementToCopy, Element copyTarget) {
        Element newCopy = null;
        if (elementToCopy != null && copyTarget != null) {
            newCopy = CopyPasting.copyPasteElement(elementToCopy, copyTarget, true);
        }
        return newCopy;
    }

    /**
     * Deletes all elements in a project under the indicated parent.
     *
     * @param parent Parent element under which you want to delete elements.
     */
    public static void deleteEditableContainerChildren(Element parent) throws ReadOnlyElementException {
        Collection<Element> elements = parent.getOwnedElement();
        ArrayList<Element> eList = new ArrayList<>();

        for (Element e : elements) {
            if (e.canBeDeleted() && e.isEditable() && (e.getHumanName().startsWith("Diagram")
                    || (e.getHumanName().startsWith("Package")
                    && !(e.getHumanName().contains("__MMSSync__") || e.getHumanName().contains("Holding Bin"))))) {
                eList.add(e);
            }
        }
        for (Element elem : eList) {
            deleteMDElement(elem);
        }
    }

    /**
     * Deletes all editable container elements in a project.
     */
    public static void clearModel() throws ReadOnlyElementException {
        if (ElementFinder.getModelRoot() == null) {
            System.out.println("Null root");
            return;
        }
        deleteEditableContainerChildren(ElementFinder.getModelRoot());
    }

    /**
     * Deletes selected element from Magic Draw, including children
     * Convenience method for the MD API.
     *
     * @param ele selected element to be deleted.
     */
    public static void deleteMDElement(Element ele) throws ReadOnlyElementException {
        ModelElementsManager.getInstance().removeElement(ele);
    }

    /**
     * Returns the element's documentation, stripped of HTML wrapper
     *
     * @param target The level element whose comment you want to return
     */
    public static String getElementDocumentation(Element target) {
        return Utils.stripHtmlWrapper(ModelHelper.getComment(target));
    }

    /**
     * Returns the value of the passed property
     *
     * @param target The property whose value you wish to inspect
     * @return
     */
    public static String getPropertyValue(Element target) {
        String value = null;
        if (target instanceof Property) {
            ValueSpecification vs = ((Property) target).getDefaultValue();
            value = getSpecificationValue(vs);
        }
        return value;
    }

    /**
     * Returns the value stored in a ValueSpecification
     *
     * @param vs The ValueSpecification to extract a value from
     * @return the value in the ValueSpecification, expressed as a string.
     */
    public static String getSpecificationValue(ValueSpecification vs) {
        String value = null;
        if (vs instanceof LiteralBoolean) {
            value = Boolean.toString(((LiteralBoolean) vs).isValue());
        }
        else if (vs instanceof LiteralInteger) {
            value = Long.toString(((LiteralInteger) vs).getValue());
        }
        else if (vs instanceof LiteralNull) {
            value = null;
        }
        else if (vs instanceof LiteralReal) {
            value = Double.toString(((LiteralReal) vs).getValue());
        }
        else if (vs instanceof LiteralString) {
            value = Utils.stripHtmlWrapper(((LiteralString) vs).getValue());
        }
        else if (vs instanceof LiteralUnlimitedNatural) {
            value = Long.toString(((LiteralUnlimitedNatural) vs).getValue());
        }
        return value;
    }

    /**
     * Sets the comments of the target element, adding the necessary html
     * wrapper
     *
     * @param target        The level element whose comment you want to change
     * @param documentation The new comments for the target element
     */
    public static void setElementDocumentation(Element target, String documentation) {
        ModelHelper.setComment(target, documentation);
    }

    /**
     * Sets the name of the passed NamedElement.
     *
     * @param target  The NamedElement to rename
     * @param newName The new name for the element
     */
    public static void setElementName(NamedElement target, String newName) {
        target.setName(newName);
    }

    /*****************************************************************************************
     *
     * MMS Stereotype Functions
     *
     *****************************************************************************************/

    //????
    @Deprecated
    public static void prepareMMS(String url, String site) {
        String s;
        s = "Model Management System";
        if (StereotypesHelper.hasStereotype(ElementFinder.getModelRoot(), s)) {
            System.out.println("Found " + s);
        }
        Stereotype mms = StereotypesHelper.getStereotype(project, s);
        if (mms != null) {
            System.out.println("Stereotype found " + s);
        }
        s = "ModelManagementSystem";
        if (StereotypesHelper.hasStereotype(ElementFinder.getModelRoot(), s)) {
            System.out.println("Found " + s);
        }
        mms = StereotypesHelper.getStereotype(project, s);
        if (mms != null) {
            System.out.println("Stereotype found " + s);
        }


        System.out.println(StereotypesHelper.canApplyStereotype(ElementFinder.getModelRoot(), mms));
        if (!StereotypesHelper.hasStereotype(ElementFinder.getModelRoot(), "ModelManagementSystem")) {
            StereotypesHelper.addStereotype(ElementFinder.getModelRoot(), mms);
        }
        StereotypesHelper.setStereotypePropertyValue(ElementFinder.getModelRoot(), mms, "MMS Site", site);
        StereotypesHelper.setStereotypePropertyValue(ElementFinder.getModelRoot(), mms, "MMS URL", url);
    }

    //????
    @Deprecated
    private static Property getTagProperty(String name, Stereotype ster) {
        List<Element> ste = StereotypesHelper.getExtendedElements(ster);
        for (Element elem : ste) {
            if (ster.hasOwnedAttribute()) {
                List<Property> attribs = ster.getOwnedAttribute();
                for (Property tag : attribs) {
                    System.out.println(tag.getName());
                    if (tag.getName().equals(name)) {
                        List<?> value = StereotypesHelper.getStereotypePropertyValue(elem, ster, tag.getName());
                        for (Object val : value) {
                            if (val instanceof LiteralString) {
                                System.out.println(((LiteralString) val).getValue() + " " + ((LiteralString) val).isEditable());
                            }
                        }
                        return tag;
                    }
                }
            }
        }
        System.out.println(name + " not found");
        return null;
    }

    /*****************************************************************************************
     *
     * Element Creation Functions
     *
     *****************************************************************************************/

    public static Association createAssociation(Element owner, Element source, Element target) {
        Association newAssoc = ef.createAssociationInstance();
        finishElement(newAssoc, null, owner);
        Property sourceProp = createProperty(((NamedElement) target).getName(), source, null, target, "none", "", "");
        Property targetProp = createProperty(((NamedElement) source).getName(), target, null, source, "none", "", "");
        newAssoc.getMemberEnd().clear();
        newAssoc.getMemberEnd().add(0, sourceProp);
        newAssoc.getMemberEnd().add(targetProp);
        return newAssoc;
    }

    public static Class createBlock(String name, Element owner) {
        Class newBlock = createClass(name, owner);
        Element stereo = ElementFinder.getElementByID("_11_5EAPbeta_be00301_1147424179914_458922_958", Project.getProject(owner));
        if (!(stereo instanceof Stereotype)) {
            return null;
        }
        Stereotype block = (Stereotype) stereo;
        StereotypesHelper.addStereotype(newBlock, block);
        return newBlock;
    }

    public static Class createClass(String name, Element owner) {
        Class newClass = ef.createClassInstance();
        finishElement(newClass, name, owner);
        return newClass;
    }

    public static Component createComponent(String name, Element owner) {
        Component comp = ef.createComponentInstance();
        finishElement(comp, name, owner);
        return comp;
    }

    public static Constraint createConstraint(String name, Element owner, ValueSpecification spec) {
        Constraint newConstraint = ef.createConstraintInstance();
        finishElement(newConstraint, name, owner);
        if (spec != null) {
            newConstraint.setSpecification(spec);
        }
        return newConstraint;
    }

    public static Association createDirectedComposition(Element document, Element view) {
        Association assoc = ef.createAssociationInstance();
        finishElement(assoc, null, document.getOwner());
        Property source = createProperty(((NamedElement) view).getName(), document, null, view, "composite", "1", "1");
        Property target = createProperty("", assoc, null, document, "none", "1", "1");
        assoc.getMemberEnd().clear();
        assoc.getMemberEnd().add(0, source);
        assoc.getMemberEnd().add(target);
        assoc.getOwnedEnd().add(0, target);
        return assoc;
    }

    public static Dependency createDependency(String name, Element owner, Element source, Element target) {
        Dependency depd = ef.createDependencyInstance();
        setRelationshipEnds(depd, source, target);
        finishElement(depd, null, owner);
        return depd;
    }

    public static Class createDocument(String name, Element owner) {
        Class newDocument = createClass(name, owner);
        Stereotype sysmlDocument = Utils.getDocumentStereotype();
        StereotypesHelper.addStereotype(newDocument, sysmlDocument);
        return newDocument;
    }

    public static Generalization createGeneralization(String name, Element owner, Element source, Element target) {
        Generalization genr = ef.createGeneralizationInstance();
        setRelationshipEnds(genr, source, target);
        finishElement(genr, null, owner);
        return genr;
    }

    public static Package createPackage(String name, Element owner) {
        Package newPackage = ef.createPackageInstance();
        finishElement(newPackage, name, owner);
        return newPackage;
    }

    public static Property createPartProperty(String name, Element owner) {
        Property newProp = createProperty(name, owner, null, null, null, null, null);
        Element stereo = ElementFinder.getElementByID("_15_0_be00301_1199377756297_348405_2678", Project.getProject(owner));
        if (!(stereo instanceof Stereotype)) {
            return null;
        }
        Stereotype partProp = (Stereotype) stereo;
        StereotypesHelper.addStereotype(newProp, partProp);
        return newProp;
    }

    public static Property createProperty(String name, Element owner, ValueSpecification defaultValue,
                                          Element typeElement, String aggregation, String multMin, String multMax) {
        Property prop = ef.createPropertyInstance();
        finishElement(prop, name, owner);
        prop.setVisibility(VisibilityKindEnum.PUBLIC);

        if (defaultValue != null) {
            prop.setDefaultValue(defaultValue);
        }

        if (typeElement != null) {
            prop.setType((Type) typeElement);
        }

        if (aggregation != null) {
            prop.setAggregation(AggregationKindEnum.getByName(aggregation));
        }

        if (multMin != null) {
            try {
                Long spmin = new Long(multMin);
                ValueSpecification pmin = prop.getLowerValue();
                if (pmin == null) {
                    pmin = ef.createLiteralIntegerInstance();
                }
                else if (pmin instanceof LiteralInteger) {
                    ((LiteralInteger) pmin).setValue(spmin.intValue());
                }
                else if (pmin instanceof LiteralUnlimitedNatural) {
                    ((LiteralUnlimitedNatural) pmin).setValue(spmin.intValue());
                }
                prop.setLowerValue(pmin);
            } catch (NumberFormatException ignored) {
            }
        }

        if (multMax != null) {
            try {
                Long spmax = new Long(multMax);
                ValueSpecification pmax = prop.getLowerValue();
                if (pmax == null) {
                    pmax = ef.createLiteralIntegerInstance();
                }
                else if (pmax instanceof LiteralInteger) {
                    ((LiteralInteger) pmax).setValue(spmax.intValue());
                }
                else if (pmax instanceof LiteralUnlimitedNatural) {
                    ((LiteralUnlimitedNatural) pmax).setValue(spmax.intValue());
                }
                prop.setLowerValue(pmax);
            } catch (NumberFormatException en) {
            }
        }

        return prop;
    }

    public static Component createSiteCharComponent(String name, Element owner) {
        Component comp = createComponent(name, owner);
        Component genTarget = Utils.getSiteCharacterizationComponent();
        createGeneralization("", comp, comp, genTarget);
        createDependency("", comp, comp, owner);
        return comp;
    }

    @SuppressWarnings("unchecked")
    @Deprecated
    public static ValueSpecification createValueSpec(String type, String value) throws ReferenceException {
        return null;
    }

    public static Class createView(String name, Element owner) {
        Class newView = createClass(name, owner);
        Stereotype sysmlView = Utils.getViewStereotype();
        StereotypesHelper.addStereotype(newView, sysmlView);
        return newView;
    }

    /******************************************************************************************************
     *
     * Helper methods for element creation functions
     *
     ******************************************************************************************************/

    /**
     * Convenience method to fill element properties name and owner.
     *
     * @param newElement The element to be finished.
     * @param name       The name of the NamedElement. This will be applied to a NamedElement
     *                   unless name is null. This parameter will be ignored if the element is not
     *                   a NamedElement.
     * @param owner      The owner of the element to be finished.
     * @return The finished newElement.
     */
    private static Element finishElement(Element newElement, String name, Element owner) {
        if (newElement instanceof NamedElement && !(name == null || name.isEmpty())) {
            ((NamedElement) newElement).setName(name);
        }
        newElement.setOwner(owner);
        return newElement;
    }

    /**
     * Convenience method to set or update relationship ends
     *
     * @param dr
     * @param source
     * @param target
     */
    private static void setRelationshipEnds(DirectedRelationship dr, Element source, Element target) {
        ModelHelper.setClientElement(dr, source);
        ModelHelper.setSupplierElement(dr, target);
    }
}