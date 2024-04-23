import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import java.util.regex.Pattern;
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()

def NEWLINE = System.lineSeparator()
def IFS = /\|\^\|/  // Input Field Separator
def OFS = "|^|"  // Output Field Separater
def DBIFS = "\\^\\^\\^"    // Database Field Separator
def DBOFS = "^^^"    // Database Field Separator

def sectionNumber = ExecutionUtil.getDynamicProcessProperty("DPP_SectionNumber") ?: "0.0.0.0.0"

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    // INPUTS //

    int tableInstanceIndex = (props.getProperty("document.dynamic.userdefined.ddp_tableInstanceIndex") ?: "1") as int
    // println tableInstanceIndex
    def tableInstanceJson = props.getProperty("document.dynamic.userdefined.ddp_TableInstance")
    def tableInstance = tableInstanceJson ? new JsonSlurper().parseText(tableInstanceJson).Records[0] : []
    // println tableInstance
    def sourcesJson = props.getProperty("document.dynamic.userdefined.ddp_Sources")
    def source = new JsonSlurper().parseText(sourcesJson).Records[0]
    // println prettyJson(source)
    def sqlParamUserInputValuesJson = props.getProperty("document.dynamic.userdefined.ddp_sqlParamUserInputValuesJson")
    def sqlParamUserInputValues = sqlParamUserInputValuesJson ? new JsonSlurper().parseText(sqlParamUserInputValuesJson) : []
    // println sqlParamUserInputValues
    def virtualColumnsJson = props.getProperty("document.dynamic.userdefined.ddp_VirtualColumns")
    def virtualColumns = virtualColumnsJson ? new JsonSlurper().parseText(virtualColumnsJson).Records : []
    // println prettyJson(virtualColumns)

    // LOGIC //

    // --- add virtual columns ---
    // add virtual column values to the User Inputs list so that they are available for string replacements below
    def virtualColumnsMap = [:]
    if (virtualColumns) {
        virtualColumns.each { vcConfig ->
            def vcColumnLabel = vcConfig.ColumnLabel
            // println vcColumnLabel
            def vcValue = vcConfig.VirtualColumnRows?.find {it.TableInstanceId == tableInstance.TableInstanceId}?.Value
            // println vcValue
            if (vcValue) {
                sqlParamUserInputValues << [UserInputName: vcColumnLabel, DisplayValue: vcValue]
            }
        }
    }
    // println prettyJson(sqlParamUserInputValues)


    // --- set tableTitleText ---
    def tableTitleText = source.TableTitleTemplate ?: "Table Title Text Not Yet Configured"

    if (source.ResultTableType =~ /(?i)Summary/){
        tableTitleText = sectionNumber + "-1. " + tableTitleText
    }
    else if (source.ResultTableType =~ /(?i)Data/) {
        tableTitleText = sectionNumber + "-" + tableInstanceIndex.toString() + ". " + (
            tableInstance.TableTitleIsOverridden ? tableInstance.TableTitleOverride : tableTitleText
        )
    }
    println tableTitleText

    // --- process placeholders ---
    (tableTitleText =~ /\{\{(.*?)\}\}/).collect{match -> match[1]}.unique().each() { placeholder ->
        // println placeholder

        // --- process string substitutions ---
        // Process the part of the placeholder containing the string substituions
        // which is surrounded by (and delimited by) 2 #s:
        //      e.g.  {{STORAGE_CONDITION ## _ ,---##SET,ZZZ## }}
        // The above will be parsed into a LinkedHashMap:
        //      [ _ :---, SET:ZZZ]
        // Notes:
        // - the string between the ##s is a list of key/value pairs, each representing a
        //   string substitution.
        // - the key is the text to be replaced
        // - the value is the replacement
        // - the string substitution pairs are delimited by a ##
        // - all occurances will be replaced
        // - uses regex
        // - there can be any amount of whitespace before the initial ## and after the
        //   terminating ##
        // - all whitespace between the delimiting ##s is respected
        // - , is escaped by doubling ,
        ArrayList placeholderPartsArr = placeholder
            .replaceFirst(/##\s*$/,"")                              // remove ending ##
            .split(/##/)                                            // split
        String placeholderName = placeholderPartsArr.remove(0).trim()  // first el is the name withough substitutiions
        // println placeholderPartsArr
        LinkedHashMap substitutionsMap = [:]
        if (placeholderPartsArr) {
            substitutionsMap = placeholderPartsArr
                .collectEntries{ substitutionItem ->
                    ArrayList substitutionItemArr = substitutionItem
                        .split(/(?<!,),(?!,)/)                      // split on , but not ,,
                        .collect { it.replaceAll(",,",",") }        // replace ,, with ,
                    [(substitutionItemArr[0]): substitutionItemArr[1]]
                }
        }
        // println substitutionsMap

        // --- get value for placeholder ---
        // look in a few different places to get the value of the placeholder (placeholderName)

        // first, look for a match in the User Inputs
        def value = sqlParamUserInputValues?.find{it.UserInputName == placeholderName}?.DisplayValue
        // println value

        // second, look for a property that matchs (DPP first, then ddp)
        if (!value || value == null) {
            def propName = placeholderName.replaceAll(" ","_")
            // println propName
            value = ExecutionUtil.getDynamicProcessProperty("DPP_" + propName)
            if (!value) value = ExecutionUtil.getDynamicProcessProperty(propName)
            else if (!value) value = props.getProperty("document.dynamic.userdefined.ddp_" + propName)
            else if (!value) value = props.getProperty("document.dynamic.userdefined." + propName)
        }
        // println value

        // --- replace placeholders ---
        if (value) {
            // apply string replacements to value
            substitutionsMap.each { search, replace ->
                value = value.replaceAll(/$search/, replace) // will treat search string as regex
            }
            // quotes all regex metacharacters
            tableTitleText = tableTitleText.replaceAll(/\{\{${Pattern.quote(placeholder)}\}\}/, value)
        }
    }
    // watch out for xml chars
    tableTitleText = tableTitleText.replaceAll("<", "&lt;").replaceAll(">", "&gt;")
    // println tableTitleText

    tableInstance.TableTitleOverride = tableTitleText
    props.setProperty("document.dynamic.userdefined.ddp_TableInstance", prettyJson([
        Requestor: props.getProperty("document.dynamic.userdefined.ddp_Requestor"),
        Records:[tableInstance]
    ]))
    props.setProperty("document.dynamic.userdefined.ddp_tableTitleText", tableTitleText)

    // is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);

}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
