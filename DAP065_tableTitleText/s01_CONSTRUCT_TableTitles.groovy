import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
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
    // def resultTableType = props.getProperty("document.dynamic.userdefined.ddp_resultTableType")
    // println resultTableType
    def sqlParamUserInputValuesJson = props.getProperty("document.dynamic.userdefined.ddp_sqlParamUserInputValuesJson")
    def sqlParamUserInputValues = sqlParamUserInputValuesJson ? new JsonSlurper().parseText(sqlParamUserInputValuesJson) : []
    // println sqlParamUserInputValues
    def tableDefinitionJson = props.getProperty("document.dynamic.userdefined.ddp_TableDefinition")
    def tableDefinition = new JsonSlurper().parseText(tableDefinitionJson).Records[0]
    // println tableDefinition
    def sourcesJson = props.getProperty("document.dynamic.userdefined.ddp_Sources")
    def source = new JsonSlurper().parseText(sourcesJson).Records[0]
    // println prettyJson(source)
    def virtualColumnsJson = props.getProperty("document.dynamic.userdefined.ddp_VirtualColumns")
    def virtualColumns = virtualColumnsJson ? new JsonSlurper().parseText(virtualColumnsJson).Records : []
    // println prettyJson(virtualColumns)
    int tableInstanceId = props.getProperty("document.dynamic.userdefined.ddp_TableInstanceId") as int
    // println tableInstanceId

    // LOGIC //


    // Virtual Columns

    def virtualColumnsMap = [:]
    if (virtualColumns) {
        virtualColumns.each { vcConfig ->
            def vcColumnLabel = vcConfig.ColumnLabel
            // println vcColumnLabel
            def vcValue = vcConfig.VirtualColumnRows?.find {it.TableInstanceId == tableInstanceId}?.Value
            // println vcValue
            if (vcValue) {
                sqlParamUserInputValues << [UserInputName: vcColumnLabel, DisplayValue: vcValue]
            }
        }
    }
    // println prettyJson(sqlParamUserInputValues)


    // set tableTitleText
    def tableTitleText = "Table Title Text Not Yet Configured"
    def resultTableType = source.ResultTableType

    if (resultTableType =~ /(?i)Summary/){
        tableTitleText = sectionNumber + "-1. " + source.TableTitleTemplate ?: tableTitleText
    }

    else if (resultTableType =~ /(?i)Data/) {
        tableTitleText = sectionNumber + "-" + tableInstanceIndex.toString() + ". " + (tableInstance.TableTitleOverride ?: tableTitleText)
    }

    // if (resultTableType =~ /(?i)Summary/){
    //   tableTitleText = sectionNumber + "-1. " +
    //   ( tableDefinition.TableTitleText_Summary != null && tableDefinition.TableTitleText_Summary != ""
    //   ? tableDefinition.TableTitleText_Summary
    //   : tableTitleText )
    // }

    // else if (resultTableType =~ /(?i)Data/) {
    //   if (tableInstance.TableTitleOverride) {
    //     tableTitleText = sectionNumber + "-" + tableInstanceIndex.toString() + ". " +
    //     ( tableInstance.TableTitleOverride != null && tableInstance.TableTitleOverride != ""
    //     ? tableInstance.TableTitleOverride
    //     : tableTitleText )
    //   }
    //   else {
    //     tableTitleText = sectionNumber + "-" + tableInstanceIndex.toString() + ". " +
    //     ( tableDefinition.TableTitleText != null && tableDefinition.TableTitleText != ""
    //     ? tableDefinition.TableTitleText
    //     : tableTitleText )
    //   }
    // }
    // println tableTitleText

    // for tableTitleText, replace placeholders with values, apply stringReplacements
    def stringReplacementsArr = tableDefinition.TableTitleStringReplacements
    // println stringReplacementsArr

    (tableTitleText =~ /\{\{(.*?)\}\}/).collect{match -> match[1]}.unique().each() { placeholder ->
        // println placeholder

        // Process the part of the placeholder containing the string substituions
        // which is surrounded in at least 2 ##s:
        //      e.g. with quotes:     {{STORAGE_CONDITION ##" _ ":"---", "SET":"ZZZ"## }}
        //      e.g. without quotes:  {{STORAGE_CONDITION ## _ :---,SET:ZZZ## }}
        // Both of the above will be parsed into a LinkedHashMap:
        //      [ _ :---, SET:ZZZ]
        // Notes:
        // - the string between the #s is a list of key/value pairs, each representing a
        //   string substitution.
        // - the key is the text to be replaced
        // - the value is the replacement
        // - the string substitution pairs are delimited by a comma
        // - all occurances will be replaced
        // - there has to be at least 2 #s, but there can be more
        // - there can be any amount of whitespace before the inition #s and after the
        //   terminating #s
        // - keys and values can be surrounded in single or double quotes, or nothing
        // - if quotes are used
        //      - whitespace within the quotes will be respected
        //      - whitespace/stray characters outside the quotes will be ignored
        // - if dbl-qotes are not used
        //      - all whitespace between the #s is respected
        placeholderPartsArr = placeholder.split(/\s*#{2,}/,2)
        def placeholderName = ""
        def substitutions = [:]
        if (placeholderPartsArr.size() > 1) {
            placeholderName = placeholderPartsArr[0]
            substitutions = placeholderPartsArr[1]
            // println placeholderName
            // println substitutions

            // // if the whole string contains a quote
            // if (substitutions =~ /(?<!\\)["']/) {
            //     println "HAS Q"
            //     substitutions = substitutions
            //         .replaceFirst(/\s*(?=["'])/,"")
            //         .replaceFirst(/\s*#{2,}\s*$/,"")
            //         .split(/\s*,\s*(?=(?:[^"']|["'][^"']*["'])*$)/)
            //         .collectEntries{
            //             def itemArr = it.split(/\s*:\s*/)
            //             def key = itemArr[0].replaceAll(/^.*?["']/,"").replaceAll(/["'].*?$/,"")
            //             def val = itemArr[1].replaceAll(/^.*?["']/,"").replaceAll(/["'].*?$/,"")
            //             [(key):val]
            //         }
            // }
            // else {
                substitutions = substitutions
                    .replaceFirst(/#{2,}\s*$/,"")
                    .split(/,/)
                    .collectEntries{
                        def itemArr = it.split(/:/)
                        [(itemArr[0].replaceAll(/"/,"")):itemArr[1].replaceAll(/"/,"")]
                    }
            // }
        }
        else {
            placeholderName = placeholder
        }
        // println substitutions

        def value = sqlParamUserInputValues?.find{it.UserInputName == placeholderName}?.DisplayValue
        // println value
        if (!value || value == null) {
            def propName = placeholderName.replaceAll(" ","_")
            // println propName
            value = ExecutionUtil.getDynamicProcessProperty("DPP_" + propName)
            if (!value) value = ExecutionUtil.getDynamicProcessProperty(propName)
            else if (!value) value = props.getProperty("document.dynamic.userdefined.ddp_" + propName)
            else if (!value) value = props.getProperty("document.dynamic.userdefined." + propName)
        }
        // println value
        if (value) {
            substitutions.each { search, replace ->
                value = value.replaceAll(search, replace)
            }
            tableTitleText = tableTitleText.replaceAll(/\{\{$placeholder\}\}/, value)
        }
    }
    tableTitleText = tableTitleText.replaceAll("<", "&lt;").replaceAll(">", "&gt;")
    println tableTitleText

    props.setProperty("document.dynamic.userdefined.ddp_tableTitleText", tableTitleText)

    // is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);

}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
