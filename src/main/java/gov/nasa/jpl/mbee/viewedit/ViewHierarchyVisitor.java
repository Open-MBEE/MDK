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
package gov.nasa.jpl.mbee.viewedit;

import gov.nasa.jpl.mbee.model.AbstractModelVisitor;
import gov.nasa.jpl.mbee.model.Document;
import gov.nasa.jpl.mbee.model.Section;

import java.util.Stack;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class ViewHierarchyVisitor extends AbstractModelVisitor {

    private JSONObject       result;
    private Stack<JSONArray> curChildren;
    private JSONArray        nosections;

    public ViewHierarchyVisitor() {
        result = new JSONObject();
        curChildren = new Stack<JSONArray>();
        nosections = new JSONArray();
    }

    @SuppressWarnings("unchecked")
    public JSONObject getResult() {
        JSONObject res = new JSONObject();
        res.put("views", result);
        res.put("noSections", nosections);
        return res;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void visit(Document doc) {
        if (doc.getDgElement() != null) {
            curChildren.push(new JSONArray());
        }
        visitChildren(doc);
        if (doc.getDgElement() != null) {
            result.put(doc.getDgElement().getID(), curChildren.pop());
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void visit(Section sec) {
        if (sec.isView()) {
            if (sec.isNoSection())
                nosections.add(sec.getDgElement().getID());
            if (!curChildren.isEmpty())
                curChildren.peek().add(sec.getDgElement().getID());
            curChildren.push(new JSONArray());
        }
        visitChildren(sec);
        if (sec.isView()) {
            result.put(sec.getDgElement().getID(), curChildren.pop());
        }
    }
    
    public JSONObject getView2View() {
        return result;
    }
    
    public JSONArray getNosections() {
        return nosections;
    }
}
