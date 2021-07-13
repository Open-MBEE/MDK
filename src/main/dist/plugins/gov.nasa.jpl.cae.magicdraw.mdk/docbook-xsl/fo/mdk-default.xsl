<?xml version="1.0" encoding="UTF-8"?>
<!--
Version 1.0
	 by: Miyako Wilson (Georgia Tech) 05/07/21
		- handle mms-link-view and mms-cf
		- html a, br, ol, ul, ol/li, ul/li
-->

<xsl:stylesheet 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
    xmlns:fo="http://www.w3.org/1999/XSL/Format" 
    version="1.0">
    
    <xsl:import href="profile-docbook.xsl"/>
    <!-- Apply XHLTHL extension. -->
    <xsl:import href="highlight.xsl"/>
    
    <xsl:param name="toc.section.depth" select="8"/>
    <xsl:param name="section.label.includes.component.label" select="1"/>
    <xsl:param name="section.autolabel" select="1"/>
    <xsl:param name="body.start.indent" select="1"/>
    
    <!-- make pdf links blue and underline -->
    <xsl:attribute-set name="xref.properties">
        <xsl:attribute name="color">blue</xsl:attribute>
        <xsl:attribute name="text-decoration">underline</xsl:attribute>
    </xsl:attribute-set>
    
    <!-- chapter and appendix name customization - copied from mgss.xsl -->
    <xsl:param name="local.l10n.xml" select="document('')"/>
    <l:i18n xmlns:l="http://docbook.sourceforge.net/xmlns/l10n/1.0"> 
        <l:l10n language="en">
            <l:context name="title-numbered">
                <l:template name="chapter" text="%n. %t"/> 
                <l:template name="appendix" text="Appendix %n. %t"/> 
            </l:context>
        </l:l10n> 
    </l:i18n>
     
    <!-- for table - copied from mgss.xsl -->
    <xsl:template name="calsTable">
        <xsl:variable name="keep.together">
            <xsl:call-template name="pi.dbfo_keep-together"/>
        </xsl:variable>
        <xsl:for-each select="tgroup">
            <fo:table xsl:use-attribute-sets="table.table.properties">
                <xsl:if test="$keep.together != ''">
                    <xsl:attribute name="keep-together.within-column">
                        <xsl:value-of select="$keep.together"/>
                    </xsl:attribute>
                </xsl:if>
                <xsl:call-template name="table.frame"/>
                <xsl:if test="following-sibling::tgroup">
                    <xsl:attribute name="border-bottom-width">0pt</xsl:attribute>
                    <xsl:attribute name="border-bottom-style">none</xsl:attribute>
                    <xsl:attribute name="padding-bottom">0pt</xsl:attribute>
                    <xsl:attribute name="margin-bottom">0pt</xsl:attribute>
                    <xsl:attribute name="space-after">0pt</xsl:attribute>
                    <xsl:attribute name="space-after.minimum">0pt</xsl:attribute>
                    <xsl:attribute name="space-after.optimum">0pt</xsl:attribute>
                    <xsl:attribute name="space-after.maximum">0pt</xsl:attribute>
                </xsl:if>
                <xsl:if test="preceding-sibling::tgroup">
                    <xsl:attribute name="border-top-width">0pt</xsl:attribute>
                    <xsl:attribute name="border-top-style">none</xsl:attribute>
                    <xsl:attribute name="padding-top">0pt</xsl:attribute>
                    <xsl:attribute name="margin-top">0pt</xsl:attribute>
                    <xsl:attribute name="space-before">0pt</xsl:attribute>
                    <xsl:attribute name="space-before.minimum">0pt</xsl:attribute>
                    <xsl:attribute name="space-before.optimum">0pt</xsl:attribute>
                    <xsl:attribute name="space-before.maximum">0pt</xsl:attribute>
                </xsl:if>
                <xsl:apply-templates select="."/>
            </fo:table>
            <xsl:for-each select="mediaobject|graphic">
                <xsl:apply-templates select="."/>
            </xsl:for-each>
        </xsl:for-each>
        <xsl:apply-templates select="caption"/>
    </xsl:template>
   
    <!-- finding internal link (section) based on its data-mms-element-id.  If not found, error shows in red -->
	<xsl:template match="mms-view-link">
	   <xsl:variable name="dmeId" select="@data-mms-element-id" />
	       <xsl:choose>
	           <xsl:when test="$dmeId">
	               <xsl:choose>
		              <xsl:when test="//section[@id=$dmeId]">
       		            <fo:block>
       		                <fo:inline keep-with-next.within-line="always">
       		                    <fo:basic-link internal-destination="{$dmeId}" xsl:use-attribute-sets="xref.properties">  
       		                        <xsl:value-of select="normalize-space(substring-before(substring-after(.,':'), '.'))"/> 
       		                    </fo:basic-link>
       		                </fo:inline>
       		            </fo:block>
  		             </xsl:when>    
     		         <xsl:otherwise>
     		            <fo:block color ="red">
     		                <fo:inline keep-with-next.within-line="always">
     		                    <xsl:value-of select="normalize-space(substring-before(substring-after(.,':'), '.'))"/> 
     		                    [error: An internal link not found]
     		                </fo:inline>
     		            </fo:block>
     		          </xsl:otherwise>
    	           </xsl:choose>
	           </xsl:when>
	           <xsl:otherwise>
	               <fo:block color ="red">
	                   <fo:inline keep-with-next.within-line="always">
	                       <xsl:value-of select="normalize-space(substring-before(substring-after(.,':'), '.'))"/> 
	                       [error: An internal link not defined]
	                   </fo:inline>
	               </fo:block>
	           </xsl:otherwise>
		  </xsl:choose>
	</xsl:template>
    
    <!--"[cf:Create Viewpoint Methods.vlink]" to "Create Viewpoint Methods"--> 
    <xsl:template match="mms-cf">
    	<xsl:value-of select="substring-before(substring-after(.,':'), '-.')"/>
    </xsl:template>
    <xsl:template match="a">
        <xsl:variable name="href" select="@href" />
        <xsl:if test="$href != ''">
            <fo:basic-link external-destination="url({$href})" xsl:use-attribute-sets="xref.properties">  
                <xsl:value-of select="."/> 
            </fo:basic-link>
        </xsl:if>    
    </xsl:template>    
    
    <xsl:template match="p|span">
        <xsl:apply-templates/>
    </xsl:template>     
    <xsl:template match="br" >
        <xsl:text>&#x20;&#x20;</xsl:text>
        <xsl:text>&#xA;</xsl:text>
    </xsl:template>         
        
    <xsl:template match="ol|ul">
        <fo:list-block margin-top="5pt">
            <xsl:apply-templates/>
        </fo:list-block>
    </xsl:template>
    
    <xsl:template match="ul/li">
        <fo:list-item margin-top="10pt">
            <fo:list-item-label end-indent="label-end()">
                <fo:block>&#x02022;</fo:block> <!-- &bullet -->
            </fo:list-item-label>
            <fo:list-item-body start-indent="body-start()">
                <fo:block> <xsl:value-of select="."/></fo:block> 
            </fo:list-item-body>
        </fo:list-item>
    </xsl:template>
    
    <xsl:template match="ol/li">
        <fo:list-item margin-top="10pt">
            <fo:list-item-label end-indent="label-end()">
                <fo:block><xsl:number count="li" level="single" format="1."/></fo:block>
            </fo:list-item-label>
            <fo:list-item-body start-indent="body-start()">
                <fo:block><xsl:value-of select="."/></fo:block> 
            </fo:list-item-body>
        </fo:list-item>
    </xsl:template>    
       
</xsl:stylesheet>