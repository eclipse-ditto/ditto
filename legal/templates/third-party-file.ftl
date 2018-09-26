<#function licenseFormat licenses>
    <#assign result = ""/>
    <#list licenses as license>
        <#assign result = result + "[" + license + "](licenses/" + license + ".txt)" + ", " />
    </#list>
    <#assign filteredResult = result?substring(0, result?last_index_of(',')) />
    <#return filteredResult>
</#function>
<#function dependencyMapEntry e>
    <#assign p = e.getKey()/>
    <#assign licenses = e.getValue()/>
    <#return "## " + p.name + " (" + p.version + ")"
        + "\n\n * Maven coordinates: `" + p.groupId + ":" + p.artifactId + ":" + p.version + "`"
        + "\n * License: " + licenseFormat(licenses)
        + "\n * Project: " + p.url
        + "\n * Source: " + p.scm.url
            ?replace('(git@|scm:git:git://|git://|http://)','https://','r')
            ?replace('.git','')
            ?replace('https://github.com:','https://github.com/')
        + "\n\n">
</#function>
# Third-party Content

<#if dependencyMap?size == 0>
The project has no dependencies.
<#else>
    <#list dependencyMap as e>
${dependencyMapEntry(e)}
    </#list>
</#if>
