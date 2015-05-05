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
package gov.nasa.jpl.mbee.model;

import gov.nasa.jpl.mbee.DocGen3Profile;
import gov.nasa.jpl.mbee.DocGenUtils;
import gov.nasa.jpl.mbee.lib.GeneratorUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Property;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;

public abstract class HierarchicalPropertiesTable extends Table {
    protected int              floatingPrecision;
    protected int              maxDepth;
    protected List<String>     topIncludeTypeName;
    protected List<String>     topExcludeTypeName;
    protected List<Stereotype> topIncludeStereotype;
    protected List<Stereotype> topExcludeStereotype;
    protected List<String>     topIncludeName;
    protected List<String>     topExcludeName;
    protected int              topAssociationType;
    protected List<String>     topOrder;
    protected boolean          showType;
    protected boolean          includeInherited;

    public boolean isIncludeInherited() {
        return includeInherited;
    }

    public void setIncludeInherited(boolean includeInherited) {
        this.includeInherited = includeInherited;
    }

    public void setFloatingPrecision(int floatingPrecision) {
        this.floatingPrecision = floatingPrecision;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public void setTopIncludeTypeName(List<String> topIncludeTypeName) {
        this.topIncludeTypeName = topIncludeTypeName;
    }

    public void setTopExcludeTypeName(List<String> topExcludeTypeName) {
        this.topExcludeTypeName = topExcludeTypeName;
    }

    public void setTopIncludeStereotype(List<Stereotype> topIncludeStereotype) {
        this.topIncludeStereotype = topIncludeStereotype;
    }

    public void setTopExcludeStereotype(List<Stereotype> topExcludeStereotype) {
        this.topExcludeStereotype = topExcludeStereotype;
    }

    public void setTopIncludeName(List<String> topIncludeName) {
        this.topIncludeName = topIncludeName;
    }

    public void setTopExcludeName(List<String> topExcludeName) {
        this.topExcludeName = topExcludeName;
    }

    public void setTopAssociationType(int topAssociationType) {
        this.topAssociationType = topAssociationType;
    }

    public void setTopOrder(List<String> topOrder) {
        this.topOrder = topOrder;
    }

    public void setShowType(boolean showType) {
        this.showType = showType;
    }

    public int getFloatingPrecision() {
        return floatingPrecision;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public List<String> getTopIncludeTypeName() {
        return topIncludeTypeName;
    }

    public List<String> getTopExcludeTypeName() {
        return topExcludeTypeName;
    }

    public List<Stereotype> getTopIncludeStereotype() {
        return topIncludeStereotype;
    }

    public List<Stereotype> getTopExcludeStereotype() {
        return topExcludeStereotype;
    }

    public List<String> getTopIncludeName() {
        return topIncludeName;
    }

    public List<String> getTopExcludeName() {
        return topExcludeName;
    }

    public int getTopAssociationType() {
        return topAssociationType;
    }

    public List<String> getTopOrder() {
        return topOrder;
    }

    public boolean isShowType() {
        return showType;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void initialize() {
        super.initialize();
        Integer maxDepth = (Integer)GeneratorUtils.getObjectProperty(dgElement,
                DocGen3Profile.hierarchicalPropertiesTableStereotype, "maxDepth", 0);
        List<String> topIncludeTypeName = DocGenUtils
                .getElementNames((Collection<NamedElement>)GeneratorUtils.getListProperty(dgElement,
                        DocGen3Profile.hierarchicalPropertiesTableStereotype, "topIncludeTypeName",
                        new ArrayList<Property>()));
        List<String> topExcludeTypeName = DocGenUtils
                .getElementNames((Collection<NamedElement>)GeneratorUtils.getListProperty(dgElement,
                        DocGen3Profile.hierarchicalPropertiesTableStereotype, "topExcludeTypeName",
                        new ArrayList<Property>()));
        List<Stereotype> topIncludeStereotype = (List<Stereotype>)GeneratorUtils.getListProperty(dgElement,
                DocGen3Profile.hierarchicalPropertiesTableStereotype, "topIncludeStereotype",
                new ArrayList<Stereotype>());
        List<Stereotype> topExcludeStereotype = (List<Stereotype>)GeneratorUtils.getListProperty(dgElement,
                DocGen3Profile.hierarchicalPropertiesTableStereotype, "topExcludeStereotype",
                new ArrayList<Stereotype>());
        List<String> topIncludeName = DocGenUtils.getElementNames((Collection<NamedElement>)GeneratorUtils
                .getListProperty(dgElement, DocGen3Profile.hierarchicalPropertiesTableStereotype,
                        "topIncludeName", new ArrayList<Property>()));
        List<String> topExcludeName = DocGenUtils.getElementNames((Collection<NamedElement>)GeneratorUtils
                .getListProperty(dgElement, DocGen3Profile.hierarchicalPropertiesTableStereotype,
                        "topExcludeName", new ArrayList<Property>()));
        Integer topAssociationType = (Integer)GeneratorUtils.getObjectProperty(dgElement,
                DocGen3Profile.hierarchicalPropertiesTableStereotype, "topAssociationType", 0);
        List<String> topOrder = DocGenUtils.getElementNames((Collection<NamedElement>)GeneratorUtils
                .getListProperty(dgElement, DocGen3Profile.hierarchicalPropertiesTableStereotype, "topOrder",
                        new ArrayList<Property>()));
        if (!topIncludeName.isEmpty() && topOrder.isEmpty())
            topOrder = topIncludeName;

        setFloatingPrecision((Integer)GeneratorUtils.getObjectProperty(dgElement,
                DocGen3Profile.precisionChoosable, "floatingPrecision", -1));
        setMaxDepth(maxDepth);
        setTopIncludeTypeName(topIncludeTypeName);
        setTopExcludeTypeName(topExcludeTypeName);
        setTopIncludeStereotype(topIncludeStereotype);
        setTopExcludeStereotype(topExcludeStereotype);
        setTopIncludeName(topIncludeName);
        setTopExcludeName(topExcludeName);
        setTopAssociationType(topAssociationType);
        setTopOrder(topOrder);
        setIncludeInherited((Boolean)GeneratorUtils.getObjectProperty(dgElement,
                DocGen3Profile.inheritedChoosable, "includeInherited", false));
    }
}
