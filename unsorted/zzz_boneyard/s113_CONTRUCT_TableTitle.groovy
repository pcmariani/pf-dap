import java.util.Properties;
import java.io.InputStream;
import com.boomi.execution.ExecutionUtil;
import groovy.json.JsonSlurper

logger = ExecutionUtil.getBaseLogger();

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    // def root = new JsonSlurper().parse(is)

    def tableInstanceJson = props.getProperty("document.dynamic.userdefined.ddp_TableInstance")
    def userInputValues = new JsonSlurper().parseText(tableInstanceJson).Records[0].UserInputValues
    // println userInputValues

    def tableDefinitionJson = props.getProperty("document.dynamic.userdefined.ddp_TableDefinition")
    def tableDefinition = new JsonSlurper().parseText(tableDefinitionJson).Records[0]
    // println tableDefinition
    def tableTitleText = tableDefinition.TableTitleText
    // println tableTitleText
    def stringReplacementsArr = tableDefinition.TableTitleStringReplacements
    // println stringReplacementsArr

    def sourcesJson = props.getProperty("document.dynamic.userdefined.ddp_Sources")
    def sources = new JsonSlurper().parseText(sourcesJson).Records
    println sources


    // if (tableTitle.contains("<TableNameVar>")) {
    //     String sqlParams = props.getProperty("document.dynamic.userdefined.ddp_sqlParams")
    //         .replaceAll(" ","")
    //     // .replaceAll(";","_")
    //     // println sqlParams
    //     tableTitle = tableTitle.replaceAll("TableNameVar", "TableNameVar_" + sqlParams)
    //     // println tableTitle
    // }
    // // logger.warning("tableTitle: " + tableTitle)

    // String tableTitleWithReplacements = applyPlaceholderReplacements(tableTitleText, props, stringReplacementsArr)
    // println tableTitleWithReplacements
    // props.setProperty("document.dynamic.userdefined.ddp_tableTitleWithReplacements", tableTitleWithReplacements)

    dataContext.storeStream(is, props);
}

private String applyPlaceholderReplacements(String str, Properties props, ArrayList stringReplacementsArr) {
    /*
     * Iterate over placeholders. Send placeholder names into closure as 'name'.
     * regex: <(.*?)> :  Capture anything inside <...>
     */
    (str =~ /<(.*?)>/).collect{match -> match[1]}.unique().each() { name ->
        def nameWithUnderscores = name.replaceAll(" ","_")
        String replacementValue = (((
                 props.getProperty("document.dynamic.userdefined.ddp_" + nameWithUnderscores) ?:
                 props.getProperty("document.dynamic.userdefined." + nameWithUnderscores)
            // ) ?: ExecutionUtil.getDynamicProcessProperty("DPP_" + nameWithUnderscores)
            // ) ?: ExecutionUtil.getDynamicProcessProperty(nameWithUnderscores)
        ))
            ) ?: "NO_VALUE"

        name = name.replaceAll(/([()])/,/\\$1/) // escape regex metachars
        Pattern pattern = Pattern.compile(/<${name}>/)
        str = pattern.matcher(str).replaceAll(applyStringReplacements(name, replacementValue, stringReplacementsArr))
    }
    return str
        .replaceAll(/\[[^\]\[]*?NO_VALUE.*?\]/, "") // Enclose section containing placeholder in squre brackets. If the replacement is NO_VALUE the entire bracketted section will be removed.
        .replaceAll(/NO_VALUE/, "") // If the placeholder isn't found AND it's not enclosed in brackets, remove the ugly NO_VALUE
        .replaceAll(/[\[\]]/, "")
        .replaceAll(/ {2,}/, " ")
        .replaceAll(/^ +/, "")
        .replaceAll(/ +$/, "")
}

private String applyStringReplacements(String name, String replacementValue, ArrayList stringReplacementsArr) {
  stringReplacementsArr.findAll{it.placeholderName == name}.each{
    replacementValue = replacementValue.replaceAll(it.searchFor, it.replaceWith)
  }
  return replacementValue
}
