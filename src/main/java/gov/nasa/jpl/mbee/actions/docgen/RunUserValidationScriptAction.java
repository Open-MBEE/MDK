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
package gov.nasa.jpl.mbee.actions.docgen;

import gov.nasa.jpl.mbee.DgvalidationDBSwitch;
import gov.nasa.jpl.mbee.dgvalidation.Suite;
import gov.nasa.jpl.mbee.lib.Utils;
import gov.nasa.jpl.mbee.model.UserScript;
import gov.nasa.jpl.mgss.mbee.docgen.validation.ValidationSuite;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nomagic.magicdraw.actions.MDAction;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.GUILog;

public class RunUserValidationScriptAction extends MDAction {

    private static final long serialVersionUID = 1L;
    private UserScript         scripti;
    public static final String actionid = "RunValidationScript";

    public RunUserValidationScriptAction(UserScript us) {
        super(null, "Run Validation Script", null, null);
        scripti = us;
        String name = scripti.getStereotypeName();
        if (name != null)
            this.setName("Run " + name + " Validation");
    }

    public RunUserValidationScriptAction(UserScript us, boolean useid) {
        super(actionid, "Run Validation Script", null, null);
        scripti = us;
        String name = scripti.getStereotypeName();
        if (name != null)
            this.setName("Run " + name + " Validation");
    }

    @SuppressWarnings("unchecked")
    @Override
    public void actionPerformed(ActionEvent event) {
        GUILog log = Application.getInstance().getGUILog();
        /*
         * String fix = "FixNone"; List<String> fixes = new ArrayList<String>();
         * fixes.add("FixSelected"); fixes.add("FixAll"); fixes.add("FixNone");
         * fix = Utils.getUserDropdownSelectionForString("Choose Fix Mode",
         * "Choose Fix Mode", fixes, fixes); if (fix == null) fix = "FixNone";
         * Map<String, Object> inputs = new HashMap<String, Object>();
         * inputs.put("FixMode", fix);
         */
        Map<String, Object> inputs = new HashMap<String, Object>();
        Map<?, ?> o = scripti.getScriptOutput(inputs);
        if (o != null && o.containsKey("DocGenValidationOutput")) {
            Object l = o.get("DocGenValidationOutput");
            if (l instanceof List) {
                Utils.displayValidationWindow((List<ValidationSuite>)l, "User Validation Script Results");
            }
        } else if (o != null && o.containsKey("docgenValidationOutput")) {
            Object l = o.get("docgenValidationOutput");
            if (l instanceof List) {
                DgvalidationDBSwitch s = new DgvalidationDBSwitch();
                List<ValidationSuite> vs = new ArrayList<ValidationSuite>();
                for (Object object: (List<?>)l) {
                    if (object instanceof Suite)
                        vs.add((ValidationSuite)s.doSwitch((Suite)object));
                }
                Utils.displayValidationWindow(vs, "User Validation Script Results");
            }
        } else
            log.log("script has no validation output!");

    }
}
