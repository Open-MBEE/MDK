package gov.nasa.jpl.mbee;

import java.util.ArrayList;
import java.util.List;

import gov.nasa.jpl.mbee.actions.systemsreasoner.CreateInstanceMenuAction;
import gov.nasa.jpl.mbee.actions.systemsreasoner.CreateSpecificAction;
import gov.nasa.jpl.mbee.actions.systemsreasoner.DespecifyAction;
import gov.nasa.jpl.mbee.actions.systemsreasoner.Instance2BSTAction;
import gov.nasa.jpl.mbee.actions.systemsreasoner.SRAction;
import gov.nasa.jpl.mbee.actions.systemsreasoner.ValidateAction;
import gov.nasa.jpl.mbee.systemsreasoner.validation.IndeterminateProgressMonitorProxy;
import gov.nasa.jpl.mbee.systemsreasoner.validation.actions.CreateInstanceAction;
import gov.nasa.jpl.mbee.actions.systemsreasoner.SpecifyAction;

import com.google.common.collect.Lists;
import com.nomagic.actions.ActionsCategory;
import com.nomagic.actions.ActionsManager;
import com.nomagic.actions.NMAction;
import com.nomagic.magicdraw.actions.ActionsGroups;
import com.nomagic.magicdraw.actions.BrowserContextAMConfigurator;
import com.nomagic.magicdraw.actions.DiagramContextAMConfigurator;
import com.nomagic.magicdraw.actions.MDAction;
import com.nomagic.magicdraw.actions.MDActionsCategory;
import com.nomagic.magicdraw.ui.browser.Node;
import com.nomagic.magicdraw.ui.browser.Tree;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.magicdraw.uml.symbols.PresentationElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Classifier;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.InstanceSpecification;

public class SRConfigurator implements BrowserContextAMConfigurator, DiagramContextAMConfigurator {
	
	public static final String NAME = "Systems Reasoner";
	
	private SRAction validateAction = null, specAction = null, despecAction = null, createSpecificAction = null, instance2BSTAction = null, createInstanceMenuAction = null;
	
    @Override
    public int getPriority() {
        return 0; //medium
    }

    @Override
    public void configure(ActionsManager manager, Tree tree) {
    	final List<Element> elements = new ArrayList<Element>();
    	for (final Node n : tree.getSelectedNodes()) {
    		if (n.getUserObject() instanceof Element) {
    			elements.add((Element) n.getUserObject());
    		}
    	}
    	configure(manager, elements);
    }
    
	@Override
	public void configure(ActionsManager manager, DiagramPresentationElement diagram,
            PresentationElement[] selected, PresentationElement requestor) {
		final List<Element> elements = new ArrayList<Element>();
		for (final PresentationElement pe : selected) {
			if (pe.getElement() != null) {
				elements.add(pe.getElement());
			}
		}
		configure(manager, elements);
	}
	
	protected void configure(ActionsManager manager, List<Element> elements) {
    	// refresh the actions for every new click (or selection)
    	validateAction = null;
    	specAction = null;                  
    	despecAction = null;
    	//copyAction = null;
    	createSpecificAction = null;
    	createInstanceMenuAction = null;
    	instance2BSTAction = null;
    	
        ActionsCategory category = (ActionsCategory)manager.getActionFor("SRMain");
        if (category == null) {
            category = new MDActionsCategory("SRMain", "Systems Reasoner", null, ActionsGroups.APPLICATION_RELATED);
            category.setNested(true);
            //manager.addCategory(0, category);
        }
        manager.removeCategory(category);
    	
    	if (elements.size() > 1) {
    		category = handleMultipleNodes(category, manager, elements);
    	} 
    	else if (elements.size() == 1) {
    		category = handleSingleNode(category, manager, elements.get(0));
    	}
    	else {
    		return;
    	}
    	
    	if (category == null) {
    		return;
    	}
    	manager.addCategory(0, category);
    	
    	category.addAction(validateAction);        
    	category.addAction(specAction);
    	category.addAction(despecAction);
    	//category.addAction(copyAction);
    	category.addAction(createSpecificAction);
    	category.addAction(createInstanceMenuAction);
    	category.addAction(instance2BSTAction);
    	
    	//System.out.println("Instance2BST: " + instance2BSTAction.getClass().getCanonicalName());
        
        // Clear out the category of unused actions
    	final List<NMAction> clonedActions = Lists.newArrayList(category.getActions());
    	category.getActions().clear();
        /*for (NMAction action : clonedActions) {
        	if (action != null) {
        		category.getActions().add(IndeterminateProgressMonitorProxy.doubleWrap((MDAction) action, "Systems Reasoner"));
        	}
        }*/
        
        //System.out.println("Instance2BST2: " + category.getActions().get(category.getActions().size() - 1).getClass().getCanonicalName());
    	
        category.setUseActionForDisable(true);
        
        if (category.isEmpty()) {
        	final MDAction mda = new MDAction(null, null, null, "null");
        	mda.updateState();
        	mda.setEnabled(false);
        	category.addAction(mda);
        }
	}
    
    public ActionsCategory handleMultipleNodes(ActionsCategory category, ActionsManager manager, List<Element> elements) {
    	//final List<Element> validatableElements = new ArrayList<Element>();
    	//boolean hasClassifier = false, hasInstance = false;
    	final List<Classifier> classifiers = new ArrayList<Classifier>();
    	final List<InstanceSpecification> instances = new ArrayList<InstanceSpecification>();
    	final List<Element> validatableElements = new ArrayList<Element>();
    	boolean hasUneditable = false;
    	
    	for (Element element : elements) {
	    	if (element != null) {
		    	if (element instanceof Classifier) {
		    		classifiers.add((Classifier) element);
		    		validatableElements.add(element);
		    	}
		    	else if (element instanceof InstanceSpecification) {
		    		instances.add((InstanceSpecification) element);
		    		validatableElements.add(element);
		    	}
		    	
		    	if (!hasUneditable && !element.isEditable()) {
		    		hasUneditable = true;
		    	}
	    	}
    	}	
    	
    	// if nothing in classes, disable category and return it
    	if (validatableElements.isEmpty()) {
    		//category = disableCategory(category);
    		return null;
    	}
    	
    	// otherwise, add the classes to the ValidateAction action
		validateAction = new ValidateAction(validatableElements);
		
		// add the action to the actions category
		category.addAction(validateAction);
		
		if (!classifiers.isEmpty()) {
			specAction = new SpecifyAction(classifiers);
			if (hasUneditable) {
				specAction.disable("Not Editable");
			}
			
			despecAction = new DespecifyAction(classifiers);
			if (hasUneditable) {
				despecAction.disable("Not Editable");
			}
			
			boolean hasGeneralization = false;
			for (final Classifier classifier : classifiers) {
				if (!classifier.getGeneralization().isEmpty()) {
					hasGeneralization = true;
					break;
				}
			}
			if (!hasGeneralization) {
				despecAction.disable("No Generalizations");
			}
		}
		
		if (!instances.isEmpty()) {
			instance2BSTAction = new Instance2BSTAction(instances);
		}
		
    	return category;
    }
    
    public ActionsCategory handleSingleNode(ActionsCategory category, ActionsManager manager, Element element) {
    	if (element == null)
    		return null;
        
        //copyAction = new CopyAction(target);
        
    	// check target instanceof
        /*if (target instanceof Activity) {
        	Activity active = (Activity) target;
        	copyAction = new CopyAction(active);
        }*/
        if (element instanceof Classifier) {
        	final Classifier classifier = (Classifier) element;
        	validateAction = new ValidateAction(classifier);
        	specAction = new SpecifyAction(classifier);
        	despecAction = new DespecifyAction(classifier);
        	if (!element.isEditable()) {
        		specAction.disable("Locked");
        		despecAction.disable("Locked");
        	}
        	//copyAction = new CopyAction(clazz);
        	createSpecificAction = new CreateSpecificAction(classifier);
        	createInstanceMenuAction = new CreateInstanceMenuAction(classifier);
        	
        	if (despecAction != null && classifier.getGeneralization().isEmpty()) {
        		despecAction.disable("No Generalizations");
        	}
        }
        else if (element instanceof InstanceSpecification) {
        	final InstanceSpecification instance = (InstanceSpecification) element;
        	validateAction = new ValidateAction(instance);
        	instance2BSTAction = new Instance2BSTAction(instance);
        }
        else {
        	return null;
        }
        /*if (target instanceof Classifier) {
        	Classifier clazzifier = (Classifier) target;
        	copyAction = new CopyAction(clazzifier);
        }*/
        
        return category;
    }
    
    public static ActionsCategory disableCategory(ActionsCategory category) {
    	// once all the categories are disabled, the action category will be disabled
    	// this is defined in the configure method: category.setNested(true);
    	for (NMAction s: category.getActions()) {
    		if (s instanceof SRAction) {
	    		SRAction sra = (SRAction) s;
	    		sra.disable("Not Editable");
    		}
        }
    	return category;
    }
}
