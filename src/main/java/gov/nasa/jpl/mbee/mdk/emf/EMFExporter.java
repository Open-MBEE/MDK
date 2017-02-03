/*******************************************************************************
 * Copyright (c) <2013>, California Institute of Technology ("Caltech").  
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
 *
 * @author Johannes Gross
 ******************************************************************************/
package gov.nasa.jpl.mbee.mdk.emf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.nomagic.ci.persistence.IAttachedProject;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.core.ProjectUtilities;
import com.nomagic.magicdraw.esi.EsiUtils;
import com.nomagic.magicdraw.esi.EsiUtilsInternal;
import com.nomagic.uml2.ext.jmi.helpers.ModelHelper;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.auxiliaryconstructs.mdmodels.Model;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdinternalstructures.Connector;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;
import com.nomagic.uml2.ext.magicdraw.metadata.UMLPackage;
import gov.nasa.jpl.mbee.mdk.api.function.TriFunction;
import gov.nasa.jpl.mbee.mdk.api.incubating.MDKConstants;
import gov.nasa.jpl.mbee.mdk.api.incubating.convert.Converters;
import gov.nasa.jpl.mbee.mdk.api.stream.MDKCollectors;
import gov.nasa.jpl.mbee.mdk.json.JacksonUtils;
import gov.nasa.jpl.mbee.mdk.lib.Utils;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public class EMFExporter implements BiFunction<Element, Project, ObjectNode> {
    @Override
    public ObjectNode apply(Element element, Project project) {
        return convert(element, project);
    }

    private static ObjectNode convert(Element element, Project project) {
        return convert(element, project, false);
    }

    private static ObjectNode convert(Element element, Project project, boolean nestedValueSpecification) {
        if (element == null) {
            return null;
        }
        ObjectNode objectNode = JacksonUtils.getObjectMapper().createObjectNode();
        for (PreProcessor preProcessor : PreProcessor.values()) {
            if (nestedValueSpecification && preProcessor == PreProcessor.VALUE_SPECIFICATION) {
                continue;
            }
            try {
                objectNode = preProcessor.getFunction().apply(element, project, objectNode);
            } catch (RuntimeException e) {
                e.printStackTrace();
                System.err.println(element);
            }
            if (objectNode == null) {
                return null;
            }
        }
        for (EStructuralFeature eStructuralFeature : element.eClass().getEAllStructuralFeatures()) {
            ExportFunction function = Arrays.stream(EStructuralFeatureOverride.values())
                    .filter(override -> override.getPredicate().test(element, eStructuralFeature)).map(EStructuralFeatureOverride::getFunction)
                    .findAny().orElse(DEFAULT_E_STRUCTURAL_FEATURE_FUNCTION);
            try {
                objectNode = function.apply(element, project, eStructuralFeature, objectNode);
            } catch (RuntimeException e) {
                e.printStackTrace();
                System.err.println(element);
            }
            if (objectNode == null) {
                return null;
            }
        }
        return objectNode;
    }

    public static String getEID(EObject eObject) {
        if (eObject == null) {
            return null;
        }
        Project project;
        if (eObject instanceof Model && (project = Project.getProject((Model) eObject)).getPrimaryModel() == eObject) {
            return project.getPrimaryProject().getProjectID() + MDKConstants.PRIMARY_MODEL_ID_SUFFIX;
        }
        if (eObject instanceof InstanceSpecification && ((InstanceSpecification) eObject).getStereotypedElement() != null) {
            return getEID(((InstanceSpecification) eObject).getStereotypedElement()) + MDKConstants.APPLIED_STEREOTYPE_INSTANCE_ID_SUFFIX;
        }
        /*if (eObject instanceof TimeExpression && ((TimeExpression) eObject).get_timeEventOfWhen() != null) {
            return getEID(((TimeExpression) eObject).get_timeEventOfWhen()) + MDKConstants.TIME_EXPRESSION_ID_SUFFIX;
        }*/
        if (eObject instanceof ValueSpecification && ((ValueSpecification) eObject).getOwningSlot() != null) {
            ValueSpecification slotValue = (ValueSpecification) eObject;
            return getEID(slotValue.getOwningSlot()) + MDKConstants.SLOT_VALUE_ID_SEPARATOR + slotValue.getOwningSlot().getValue().indexOf(slotValue) + "-" + slotValue.eClass().getName().toLowerCase();
        }
        if (eObject instanceof Slot) {
            Slot slot = (Slot) eObject;
            if (slot.getOwningInstance() != null && ((Slot) eObject).getDefiningFeature() != null) {
                return getEID(slot.getOwner()) + MDKConstants.SLOT_ID_SEPARATOR + getEID(slot.getDefiningFeature());
            }
        }
        return EcoreUtil.getID(eObject);
    }

    private static void dumpUMLPackageLiterals() {
        for (Field field : UMLPackage.Literals.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                try {
                    field.setAccessible(true);
                    Object o = field.get(null);
                    System.out.println(field.getName() + ": " + o);
                    if (o instanceof EReference) {
                        EReference eReference = (EReference) o;
                        System.out.println(" --- " + eReference.getEReferenceType() + " : " + eReference.getEReferenceType().getInstanceClass());
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private enum PreProcessor {
        TYPE(
                (element, project, objectNode) -> {
                    String type = element instanceof Model && !element.equals(project.getPrimaryModel()) ? "Mount" : element.eClass().getName();
                    objectNode.put(MDKConstants.TYPE_KEY, type);
                    return objectNode;
                }
        ),
        DOCUMENTATION(
                (element, project, objectNode) -> {
                    if (element instanceof Model && !element.equals(project.getPrimaryModel())) {
                        return objectNode;
                    }
                    objectNode.put("documentation", ModelHelper.getComment(element));
                    return objectNode;
                }
        ),
        APPLIED_STEREOTYPE(
                (element, project, objectNode) -> {
                    if (element instanceof Model && !element.equals(project.getPrimaryModel())) {
                        return objectNode;
                    }
                    ArrayNode applied = StereotypesHelper.getStereotypes(element).stream().map(stereotype -> TextNode.valueOf(getEID(stereotype))).collect(MDKCollectors.toArrayNode());
                    objectNode.set("_appliedStereotypeIds", applied);
                    return objectNode;
                }
        ),
        SITE_CHARACTERIZATION(
                (element, project, objectNode) -> {
                    if (element instanceof Model) {
                        return objectNode;
                    }
                    if (element instanceof Package) {
                        objectNode.put("_isSite", Utils.isSiteChar((Package) element));
                    }
                    return objectNode;
                }
        ),
        VALUE_SPECIFICATION(
                (element, project, objectNode) -> element instanceof ValueSpecification ? null : objectNode
        ),
        /*CONNECTOR_END(
                (element, project, objectNode) -> element instanceof ConnectorEnd ? null : objectNode
        ),
        DIAGRAM(
                (element, project, objectNode) -> element instanceof Diagram ? null : objectNode
        ),*/
        COMMENT(
                (element, project, objectNode) -> {
                    if (!(element instanceof Comment)) {
                        return objectNode;
                    }
                    Comment comment = (Comment) element;
                    return comment.getAnnotatedElement().size() == 1 && comment.getAnnotatedElement().iterator().next() == comment.getOwner() ? null : objectNode;
                }
        ),
        SYNC(
                (element, project, objectNode) -> element == null || element.getLocalID().endsWith(MDKConstants.SYNC_SYSML_ID_SUFFIX) ||
                        element.getOwner() != null && element.getOwner().getLocalID().endsWith(MDKConstants.SYNC_SYSML_ID_SUFFIX) ? null : objectNode
        ),
        ATTACHED_PROJECT(
                (element, project, objectNode) -> ProjectUtilities.isElementInAttachedProject(element) && !(element instanceof Model) ? null : objectNode
        ),
        VIEW(
                (element, project, objectNode) -> {
                    Stereotype viewStereotype = Utils.getViewStereotype();
                    if (viewStereotype == null || !StereotypesHelper.hasStereotypeOrDerived(element, viewStereotype)) {
                        return objectNode;
                    }
                    Constraint viewConstraint = Utils.getViewConstraint(element);
                    if (viewConstraint == null) {
                        return objectNode;
                    }
                    objectNode.set(MDKConstants.CONTENTS_KEY, DEFAULT_SERIALIZATION_FUNCTION.apply(viewConstraint.getSpecification(), project, null));
                    return objectNode;
                }
        ),
        DIAGRAM_TYPE(
                (element, project, objectNode) -> {
                    if (element instanceof Diagram) {
                        objectNode.put(MDKConstants.DIAGRAM_TYPE_KEY, ((Diagram) element).get_representation() != null ? ((Diagram) element).get_representation().getType() : null);
                    }
                    return objectNode;
                }
        ),
        MOUNT(
                (element, project, objectNode) -> {
                    if (!(element instanceof Model)
                            || element.equals(project.getPrimaryModel())
                            || objectNode.has(MDKConstants.MOUNTED_ELEMENT_ID_KEY) ) {
                        return objectNode;
                    }
                    Model model = (Model) element;
                    IAttachedProject attachedProject = ProjectUtilities.getAttachedProjects(project.getPrimaryProject()).stream().filter(ap -> ProjectUtilities.isAttachedProjectRoot(model, ap)).findAny().orElse(null);
                    if (attachedProject == null) {
                        return null;
                    }
                    if (!ProjectUtilities.isRemote(attachedProject) || attachedProject.getLocationURI().isFile()) {
                        return null;
                    }
                    objectNode.put(MDKConstants.MOUNTED_ELEMENT_ID_KEY, Converters.getElementToIdConverter().apply(project.getPrimaryModel()));
                    objectNode.put(MDKConstants.MOUNTED_ELEMENT_PROJECT_ID_KEY, attachedProject.getPrimaryProject().getProjectID());
                    objectNode.put(MDKConstants.REF_ID, EsiUtilsInternal.getCurrentBranch(attachedProject).getName());
                    objectNode.put("teamworkCloudVersion", ProjectUtilities.versionToInt(ProjectUtilities.getVersion(attachedProject).getName()));
                    //objectNode.put("_uri", ProjectDescriptorsFactory.createRemoteProjectDescriptorWithActualVersion(attachedProject.getProjectDescriptor()));
                    return objectNode;
                }
        );

        private TriFunction<Element, Project, ObjectNode, ObjectNode> function;

        PreProcessor(TriFunction<Element, Project, ObjectNode, ObjectNode> function) {
            this.function = function;
        }

        public TriFunction<Element, Project, ObjectNode, ObjectNode> getFunction() {
            return function;
        }
    }

    private static final SerializationFunction DEFAULT_SERIALIZATION_FUNCTION = (object, project, eStructuralFeature) -> {
        if (object == null) {
            return NullNode.getInstance();
        }
        else if (object instanceof Collection) {
            ArrayNode arrayNode = JacksonUtils.getObjectMapper().createArrayNode();
            try {
                for (Object o : ((Collection<?>) object)) {
                    JsonNode serialized = EMFExporter.DEFAULT_SERIALIZATION_FUNCTION.apply(o, project, eStructuralFeature);
                    if (serialized == null && o != null) {
                        // failed to serialize; taking the conservative approach and returning entire thing as null
                        return NullNode.getInstance();
                    }
                    arrayNode.add(serialized);
                }
            } catch (UnsupportedOperationException e) {
                e.printStackTrace();
                System.err.println("Object: " + object.getClass());
            }
            return arrayNode;
        }
        else if (object instanceof ValueSpecification) {
            return convert((ValueSpecification) object, project, true);
            //return fillValueSpecification((ValueSpecification) object);
        }
        else if (eStructuralFeature instanceof EReference && object instanceof EObject) {
            return EMFExporter.DEFAULT_SERIALIZATION_FUNCTION.apply(getEID(((EObject) object)), project, eStructuralFeature);
        }
        else if (eStructuralFeature.getEType() instanceof EDataType) {
            return TextNode.valueOf(EcoreUtil.convertToString((EDataType) eStructuralFeature.getEType(), object));
            //return ((Enumerator) object).getLiteral();
        }
        else if (object instanceof String) {
            return TextNode.valueOf((String) object);
        }
        else if (object instanceof Boolean) {
            return BooleanNode.valueOf((boolean) object);
        }
        else if (object instanceof Integer) {
            return IntNode.valueOf((Integer) object);
        }
        else if (object instanceof Double) {
            return DoubleNode.valueOf((Double) object);
        }
        else if (object instanceof Long) {
            return LongNode.valueOf((Long) object);
        }
        else if (object instanceof Short) {
            return ShortNode.valueOf((Short) object);
        }
        else if (object instanceof Float) {
            return FloatNode.valueOf((Float) object);
        }
        else if (object instanceof BigInteger) {
            return BigIntegerNode.valueOf((BigInteger) object);
        }
        else if (object instanceof BigDecimal) {
            return DecimalNode.valueOf((BigDecimal) object);
        }
        else if (object instanceof byte[]) {
            return BinaryNode.valueOf((byte[]) object);
        }
        // if we get here we have no idea what to do with this object
        return NullNode.getInstance();
    };

    private static final ExportFunction DEFAULT_E_STRUCTURAL_FEATURE_FUNCTION = (element, project, eStructuralFeature, objectNode) -> {
        if (!eStructuralFeature.isChangeable() || eStructuralFeature.isVolatile() || eStructuralFeature.isTransient() || eStructuralFeature.isUnsettable() || eStructuralFeature.isDerived() || eStructuralFeature.getName().startsWith("_")) {
            return EMFExporter.EMPTY_E_STRUCTURAL_FEATURE_FUNCTION.apply(element, project, eStructuralFeature, objectNode);
        }
        return EMFExporter.UNCHECKED_E_STRUCTURAL_FEATURE_FUNCTION.apply(element, project, eStructuralFeature, objectNode);
    };

    private static final ExportFunction UNCHECKED_E_STRUCTURAL_FEATURE_FUNCTION = (element, project, eStructuralFeature, objectNode) -> {
        Object value = element.eGet(eStructuralFeature);
        JsonNode serializedValue = DEFAULT_SERIALIZATION_FUNCTION.apply(value, project, eStructuralFeature);
        if (value != null && serializedValue == null) {
            System.err.println("[EMF] Failed to serialize " + eStructuralFeature + " for " + element + ": " + value + " - " + value.getClass());
            return objectNode;
        }

        String key = eStructuralFeature.getName();
        if (eStructuralFeature instanceof EReference && EObject.class.isAssignableFrom(((EReference) eStructuralFeature).getEReferenceType().getInstanceClass())
                && !ValueSpecification.class.isAssignableFrom(((EReference) eStructuralFeature).getEReferenceType().getInstanceClass())) {
            key += "Id" + (eStructuralFeature.isMany() ? "s" : "");
        }
        objectNode.put(key, serializedValue);
        return objectNode;
    };

    private static final ExportFunction EMPTY_E_STRUCTURAL_FEATURE_FUNCTION = (element, project, eStructuralFeature, objectNode) -> objectNode;

    private enum EStructuralFeatureOverride {
        ID(
                (element, eStructuralFeature) -> eStructuralFeature == element.eClass().getEIDAttribute(),
                (element, project, eStructuralFeature, objectNode) -> {
                    /*if (element instanceof ValueSpecification && !(element instanceof TimeExpression)) {
                        return objectNode;
                    }*/
                    objectNode.put(MDKConstants.SYSML_ID_KEY, getEID(element));
                    return objectNode;
                }
        ),
        OWNER(
                (element, eStructuralFeature) -> UMLPackage.Literals.ELEMENT__OWNER == eStructuralFeature,
                (element, project, eStructuralFeature, objectNode) -> {
                    Element owner = element.getOwner();
                    /*if (element instanceof ValueSpecification || owner instanceof ValueSpecification) {
                        return objectNode;
                    }*/
                    //UNCHECKED_E_STRUCTURAL_FEATURE_FUNCTION.apply(element, project, UMLPackage.Literals.ELEMENT__OWNER, objectNode);
                    // safest way to prevent circular references, like with ValueSpecifications
                    objectNode.put(MDKConstants.OWNER_ID_KEY, element instanceof Model ? project.getPrimaryProject().getProjectID() : getEID(owner));
                    return objectNode;
                }
        ),
        OWNING(
                (element, eStructuralFeature) -> eStructuralFeature.getName().startsWith("owning"),
                EMPTY_E_STRUCTURAL_FEATURE_FUNCTION
        ),
        OWNED(
                (element, eStructuralFeature) -> eStructuralFeature.getName().startsWith("owned") && !eStructuralFeature.isOrdered(),
                EMPTY_E_STRUCTURAL_FEATURE_FUNCTION
        ),
        PACKAGED_ELEMENT(
                (element, eStructuralFeature) -> UMLPackage.Literals.PACKAGE__PACKAGED_ELEMENT == eStructuralFeature || UMLPackage.Literals.COMPONENT__PACKAGED_ELEMENT == eStructuralFeature,
                EMPTY_E_STRUCTURAL_FEATURE_FUNCTION
        ),
        DIRECTED_RELATIONSHIP__SOURCE(
                (element, eStructuralFeature) -> UMLPackage.Literals.DIRECTED_RELATIONSHIP__SOURCE == eStructuralFeature,
                (element, project, eStructuralFeature, objectNode) -> {
                    objectNode.set(MDKConstants.DERIVED_KEY_PREFIX + eStructuralFeature.getName() + MDKConstants.IDS_KEY_SUFFIX, DEFAULT_SERIALIZATION_FUNCTION.apply(element.eGet(eStructuralFeature), project, eStructuralFeature));
                    return objectNode;
                }
        ),
        DIRECTED_RELATIONSHIP__TARGET(
                (element, eStructuralFeature) -> UMLPackage.Literals.DIRECTED_RELATIONSHIP__TARGET == eStructuralFeature,
                (element, project, eStructuralFeature, objectNode) -> {
                    objectNode.set(MDKConstants.DERIVED_KEY_PREFIX + eStructuralFeature.getName() + MDKConstants.IDS_KEY_SUFFIX, DEFAULT_SERIALIZATION_FUNCTION.apply(element.eGet(eStructuralFeature), project, eStructuralFeature));
                    return objectNode;
                }
        ),
        CONNECTOR__END(
                (element, eStructuralFeature) -> eStructuralFeature == UMLPackage.Literals.CONNECTOR__END,
                (element, project, eStructuralFeature, objectNode) -> {
                    Connector connector = (Connector) element;
                    // TODO Stop using Strings @donbot
                    List<List<Object>> propertyPaths = connector.getEnd().stream()
                            .map(connectorEnd -> StereotypesHelper.hasStereotype(connectorEnd, "NestedConnectorEnd") ? StereotypesHelper.getStereotypePropertyValue(connectorEnd, "NestedConnectorEnd", "propertyPath") : null)
                            .map(elements -> {
                                if (elements == null) {
                                    return new ArrayList<>(1);
                                }
                                List<Object> list = new ArrayList<>(elements.size() + 1);
                                for (Object o : elements) {
                                    list.add(o instanceof ElementValue ? ((ElementValue) o).getElement() : o);
                                }
                                return list;
                            }).collect(Collectors.toList());
                    for (int i = 0; i < propertyPaths.size(); i++) {
                        propertyPaths.get(i).add(connector.getEnd().get(i).getRole());
                    }
                    objectNode.set("_propertyPathIds", DEFAULT_SERIALIZATION_FUNCTION.apply(propertyPaths, project, eStructuralFeature));

                    return DEFAULT_E_STRUCTURAL_FEATURE_FUNCTION.apply(element, project, eStructuralFeature, objectNode);
                }
        ),
        VALUE_SPECIFICATION__EXPRESSION(
                (element, eStructuralFeature) -> eStructuralFeature == UMLPackage.Literals.VALUE_SPECIFICATION__EXPRESSION,
                /*(element, project, eStructuralFeature, objectNode) -> {
                    Expression expression = null;
                    Object object = element.eGet(UMLPackage.Literals.VALUE_SPECIFICATION__EXPRESSION);
                    if (object instanceof Expression) {
                        expression = (Expression) object;
                    }
                    objectNode.put(UMLPackage.Literals.VALUE_SPECIFICATION__EXPRESSION.getName() + MDKConstants.ID_KEY_SUFFIX, expression != null ? expression.getID() : null);
                    return objectNode;
                }*/
                EMPTY_E_STRUCTURAL_FEATURE_FUNCTION
        ),
        UML_CLASS(
                (element, eStructuralFeature) -> eStructuralFeature == UMLPackage.Literals.CLASSIFIER__UML_CLASS || eStructuralFeature == UMLPackage.Literals.PROPERTY__UML_CLASS || eStructuralFeature == UMLPackage.Literals.OPERATION__UML_CLASS,
                EMPTY_E_STRUCTURAL_FEATURE_FUNCTION
        ),
        MOUNT(
                (element, eStructuralFeature) -> element instanceof Model,
                (element, project, eStructuralFeature, objectNode) -> (objectNode.get(MDKConstants.TYPE_KEY).asText().equals("Mount") ? EMPTY_E_STRUCTURAL_FEATURE_FUNCTION : DEFAULT_E_STRUCTURAL_FEATURE_FUNCTION).apply(element, project, eStructuralFeature, objectNode)
        );

        private BiPredicate<Element, EStructuralFeature> predicate;
        private ExportFunction function;

        EStructuralFeatureOverride(BiPredicate<Element, EStructuralFeature> predicate, ExportFunction function) {
            this.predicate = predicate;
            this.function = function;
        }

        public BiPredicate<Element, EStructuralFeature> getPredicate() {
            return predicate;
        }

        public ExportFunction getFunction() {
            return function;
        }
    }

    @FunctionalInterface
    interface SerializationFunction {
        JsonNode apply(Object object, Project project, EStructuralFeature eStructuralFeature);
    }

    @FunctionalInterface
    interface ExportFunction {
        ObjectNode apply(Element element, Project project, EStructuralFeature eStructuralFeature, ObjectNode objectNode);
    }
}
