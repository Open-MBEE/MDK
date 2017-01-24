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
 ******************************************************************************/
package gov.nasa.jpl.mbee.mdk;

import com.nomagic.actions.NMAction;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import gov.nasa.jpl.mbee.mdk.actions.ValidateConstraints;
import gov.nasa.jpl.mbee.mdk.lib.Debug;

import java.awt.event.ActionEvent;
import java.lang.reflect.Method;

public class ValidateConstraintsPlugin extends MDPlugin {

    // ValidateConstraints action = null;

    /**
     *
     */
    public ValidateConstraintsPlugin() {
        this(ValidateConstraints.class);
    }

    public ValidateConstraintsPlugin(Class<? extends NMAction> cls) {
        super(cls);
    }

    // unused -- TODO -- remove after testing
    public static void doIt(ActionEvent event, Element element) {
        ValidateConstraints action = new ValidateConstraints(element);
        action.actionPerformed(event);
    }

    /*
     * (non-Javadoc)
     * 
     * @see gov.nasa.jpl.mbee.mdk.docgen.MDPlugin#initConfigurations()
     */
    @Override
    public void initConfigurations() {
        // //Debug.turnOn();
        // if ( !MDUtils.isDeveloperMode() ) {
        // Debug.outln(
        // "ValidateConstraintsPlugin will be hidden since MD is not in developer mode."
        // );
        // return;
        // }
        Debug.outln("initializing ValidateConstraintsPlugin!");

        // Method method = ClassUtils.getMethodsForName(
        // ValidateConstraintsPlugin.class, "doIt")[ 0 ];
        // TODO -- shouldn't have to look this method up and pass it--just get
        // rid of
        // method argument in addConfiguration calls below.
        Method method = getNmActionMethod();

        String category = "MDK";
        String diagramContext = "BaseDiagramContext";
        addConfiguration("MainMenu", "", ValidateConstraints.actionText, category, method, this);
        addConfiguration("ContainmentBrowserContext", "", ValidateConstraints.actionText, category, method,
                this);
        addConfiguration(diagramContext, "Class Diagram", ValidateConstraints.actionText, category,
                method, this);
        addConfiguration(diagramContext, "Activity Diagram", ValidateConstraints.actionText, category,
                method, this);
        addConfiguration(diagramContext, "SysML Block Definition Diagram",
                ValidateConstraints.actionText, category, method, this);
        addConfiguration(diagramContext, "SysML Internal Block Diagram",
                ValidateConstraints.actionText, category, method, this);
        addConfiguration(diagramContext, "DocGen 3 View Diagram", ValidateConstraints.actionText,
                category, method, this);
        addConfiguration(diagramContext, "DocGen 3 Diagram", ValidateConstraints.actionText, category,
                method, this);
        addConfiguration(diagramContext, "View Diagram", ValidateConstraints.actionText, category,
                method, this);
        addConfiguration(diagramContext, "DocumentView", ValidateConstraints.actionText, category,
                method, this);

        Debug.outln("finished initializing TestPlugin!");
    }

}
