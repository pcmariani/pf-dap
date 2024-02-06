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
    def tableInstance = tableInstanceJson ? new JsonSlurper().parseText(tableInstanceJson) : []
    // println tableInstance
    def resultTableType = props.getProperty("document.dynamic.userdefined.ddp_resultTableType")
    // println resultTableType
    def sqlParamUserInputValuesJson = props.getProperty("document.dynamic.userdefined.ddp_sqlParamUserInputValuesJson")
    def sqlParamUserInputValues = sqlParamUserInputValuesJson ? new JsonSlurper().parseText(sqlParamUserInputValuesJson) : []
    // println sqlParamUserInputValues
    def tableDefinitionJson = props.getProperty("document.dynamic.userdefined.ddp_TableDefinition")
    def tableDefinition = new JsonSlurper().parseText(tableDefinitionJson).Records[0]
    // println tableDefinition
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
                sqlParamUserInputValues << [UserInputName: vcColumnLabel, Value: vcValue]
            }
        }
    }



    // set tableTitleText
    def tableTitleText = "Table Title Text Not Yet Configured"

    if (resultTableType =~ /(?i)Summary/){
      tableTitleText = sectionNumber + "-1. " +
      ( tableDefinition.TableTitleText_Summary != null && tableDefinition.TableTitleText_Summary != ""
      ? tableDefinition.TableTitleText_Summary
      : tableTitleText )
    }

    else if (resultTableType =~ /(?i)Data/) {
      if (tableInstance.TableTitleOverride) {
        tableTitleText = sectionNumber + "-" + tableInstanceIndex.toString() + ". " +
        ( tableDefinition.TableTitleOverride != null && tableDefinition.TableTitleOverride != ""
        ? tableDefinition.TableTitleOverride
        : tableTitleText )
      }
      else {
        tableTitleText = sectionNumber + "-" + tableInstanceIndex.toString() + ". " +
        ( tableDefinition.TableTitleText != null && tableDefinition.TableTitleText != ""
        ? tableDefinition.TableTitleText
        : tableTitleText )
      }
    }
    // println tableTitleText

    // for tableTitleText, replace placeholders with values, apply stringReplacements
    def stringReplacementsArr = tableDefinition.TableTitleStringReplacements
    // println stringReplacementsArr

    (tableTitleText =~ /\{\{(.*?)\}\}/).collect{match -> match[1]}.unique().each() { name ->
      // println name
      def value = sqlParamUserInputValues?.find{it.UserInputName == name}?.Value
      // println value
      if (!value || value == null) {
        def propName = name.replaceAll(" ","_")
        // println propName
        value = ExecutionUtil.getDynamicProcessProperty("DPP_" + propName)
        if (!value) value = ExecutionUtil.getDynamicProcessProperty(propName)
        else if (!value) value = props.getProperty("document.dynamic.userdefined.ddp_" + propName)
        else if (!value) value = props.getProperty("document.dynamic.userdefined." + propName)
      }
      // println value
      if (value) {
        def replacementsArr = stringReplacementsArr.findAll{ name == it.PlaceHolder.replaceAll(/[\{\{\}\}]/,"") }
        replacementsArr.each { replacement ->
          value = value.replaceAll(replacement.SearchText, replacement.ReplaceText)
        }
        tableTitleText = tableTitleText.replaceAll(/\{\{$name\}\}/, value)
      }
    }
    tableTitleText = tableTitleText.replaceAll("<", "&lt;").replaceAll(">", "&gt;")
    // println tableTitleText

    props.setProperty("document.dynamic.userdefined.ddp_tableTitleText", tableTitleText)

    // is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);

}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
