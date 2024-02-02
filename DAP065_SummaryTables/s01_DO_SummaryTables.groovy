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

    // int reportContentItemId = (props.getProperty("document.dynamic.userdefined.ddp_ReportContentItem_DynamicTableId") ?: "1") as int
    // int tableDefinitionId = (props.getProperty("document.dynamic.userdefined.ddp_TableDefinitionId") ?: "1") as int
    def sqlParamValues = props.getProperty("document.dynamic.userdefined.ddp_sqlParamValues")
    // println sqlParamValues
    def sqlColumnNames = props.getProperty("document.dynamic.userdefined.ddp_sqlColumnNames")
    // println sqlColumnNames
    def sqlColumnNamesArr = sqlColumnNames.split(IFS) as ArrayList
    // println sqlColumnNamesArr
    int tableInstanceIndex = (props.getProperty("document.dynamic.userdefined.ddp_tableInstanceIndex") ?: "1") as int
    // println tableInstanceIndex
    int tableInstanceId = props.getProperty("document.dynamic.userdefined.ddp_TableInstanceId") as int
    // println tableInstanceId
    def virtualColumnsJson = props.getProperty("document.dynamic.userdefined.ddp_VirtualColumns")
    def virtualColumns = virtualColumnsJson ? new JsonSlurper().parseText(virtualColumnsJson).Records : []
    // println prettyJson(virtualColumns)
    def sqlParamUserInputValuesJson = props.getProperty("document.dynamic.userdefined.ddp_sqlParamUserInputValuesJson")
    def sqlParamUserInputValues = sqlParamUserInputValuesJson ? new JsonSlurper().parseText(sqlParamUserInputValuesJson) : []
    // println prettyJson(sqlParamUserInputValues)
    def pivotedDataConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_PivotedDataConfigsConsolidated")
    def pivotedDataConfigsArr = pivotedDataConfigsJson ? new JsonSlurper().parseText(pivotedDataConfigsJson)?.Records : []
    // println prettyJson(pivotedDataConfigsArr)
    // println pivotedDataConfigsArr.size()
    def groupByConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_GroupByConfigsConsolidated")
    def activeGroupByConfigsArr = groupByConfigsJson ? new JsonSlurper().parseText(groupByConfigsJson)?.Records : []
    // println prettyJson(activeGroupByConfigsArr)
    // println activeGroupByConfigsArr.size()
    def sourceJson = props.getProperty("document.dynamic.userdefined.ddp_Sources")
    def source = new JsonSlurper().parseText(sourceJson).Records
    // println prettyJson(source)



    ArrayList sortKeysArr = sqlParamUserInputValues.findAll { it.isSorted }.Value[0].split(/\s*,\s*/)
    // println sortKeysArr

    def tableDefinitionJson = props.getProperty("document.dynamic.userdefined.ddp_TableDefinition")
    def tableDefinition = new JsonSlurper().parseText(tableDefinitionJson).Records[0]
    // println tableDefinition

    // Virtual Columns
    // println sqlColumnNamesArr
    def virtualColumnsMap = [:]
    if (virtualColumns) {
        virtualColumns.each { vc ->
            // println vc
            def virtualColumnValue = vc.VirtualColumnRows?.find {it.TableInstanceId == tableInstanceId}?.Value
            // println virtualColumnValue
            int columnToInsertAfterIndex = sqlColumnNamesArr.indexOf(vc.ColumnToInsertAfter) + 1
            // println columnToInsertAfterIndex
            if (virtualColumnValue) {
                virtualColumnsMap[columnToInsertAfterIndex] = virtualColumnValue
            }
            // Add VirtualColumn Label to column names
            sqlColumnNamesArr.add(columnToInsertAfterIndex, vc.ColumnLabel)
        }
    }
    // println sqlColumnNamesArr


    def reader = new BufferedReader(new InputStreamReader(is))

    def dataMap = [:]
    def lineIndex = 0
    while ((line = reader.readLine()) != null ) {

        def lineArr = line
            .replaceFirst(/$IFS\s*$/, "${IFS}LAST_ELEMENT_IS_BLANK")
            .split(/\s*$IFS\s*/)
            .collect{ it == "LAST_ELEMENT_IS_BLANK" ? "" : it }

        // Add VirtualColumn value to lineArr
        virtualColumnsMap.each { columnToInsertAfterIndex, virtualColumnValue ->
            lineArr.add(columnToInsertAfterIndex, virtualColumnValue)
        }
        // println lineArr

        // Create the Data Table Location
        def dataTableLocation = sectionNumber + "-" + tableInstanceIndex.toString()

        // Add sqlParamValues to be parsed and included in an id.
        // This id will become the value of the attribute "link" on the td for this cell
        // which is a reference to the tableGroup with the same id
        lineArr += dataTableLocation + "###" + sqlParamValues

        if (sortKeysArr) {
            // find the item in the sortKeysArr which exists in this line make it the key for the line
            // the value will be an array of lines in case you had multiple lines for one key
            lineArr.collect {
                if (it in sortKeysArr) {
                    if (dataMap[it]) dataMap[it] << lineArr
                    else dataMap[it] = [lineArr]
                }
            }
        }
        else {
            // if we don't have a sortKeysList, just use an index for the map key to make it work with the map
            dataMap[lineIndex] = [lineArr]
        }
        lineIndex++
    }

    // sort rows
    if (sortKeysArr) {
        dataMap = dataMap.sort { sortKeysArr.indexOf(it.key) }
    }

    /* OUTPUT */
    def outData = new StringBuilder()
    dataMap.each { k, v ->
        v.each {
            outData.append(it.join(OFS) + NEWLINE)
        }
    }

    props.setProperty("document.dynamic.userdefined.ddp_sqlColumnNames", sqlColumnNamesArr.join(OFS))



    //
    // /* --- construct table title --- */
    //
    // // set tableTitleText
    // def tableTitleText = "Table Title Text Not Yet Configured"
    // if (source.ResultTableType =~ /(?i)Summary/){
    //   tableTitleText = sectionNumber + "-1. " +
    //   ( tableDefinition.TableTitleText_Summary != null && tableDefinition.TableTitleText_Summary != ""
    //   ? tableDefinition.TableTitleText_Summary
    //   : tableTitleText )
    // }
    // else if (source.ResultTableType =~ /(?i)Data/) {
    //   if (tableInstanceRoot.TableTitleOverride) {
    //     tableTitleText = sectionNumber + "-" + tableInstanceIndex.toString() + ". " +
    //     ( tableDefinition.TableTitleOverride != null && tableDefinition.TableTitleOverride != ""
    //     ? tableDefinition.TableTitleOverride
    //     : tableTitleText )
    //   }
    //   else {
    //     println tableTitleText
    //     println tableDefinition.TableTitleText
    //     tableTitleText = sectionNumber + "-" + tableInstanceIndex.toString() + ". " +
    //     ( tableDefinition.TableTitleText != null && tableDefinition.TableTitleText != ""
    //     ? tableDefinition.TableTitleText
    //     : tableTitleText )
    //   }
    // }
    // // println tableTitleText
    //
    // // for tableTitleText, replace placeholders with values, apply stringReplacements
    // def stringReplacementsArr = tableDefinition.TableTitleStringReplacements
    // // println stringReplacementsArr
    // (tableTitleText =~ /\{\{(.*?)\}\}/).collect{match -> match[1]}.unique().each() { name ->
    //   def value = valuesMap[name]
    //   // println name
    //   if (!value) {
    //     def propName = name.replaceAll(" ","_")
    //     value = ExecutionUtil.getDynamicProcessProperty("DPP_" + propName)
    //     if (!value) value = ExecutionUtil.getDynamicProcessProperty(propName)
    //     else if (!value) value = props.getProperty("document.dynamic.userdefined.ddp_" + propName)
    //     else if (!value) value = props.getProperty("document.dynamic.userdefined." + propName)
    //   }
    //   // println value
    //   if (value) {
    //     def replacementsArr = stringReplacementsArr.findAll{ name == it.PlaceHolder.replaceAll(/[\{\{\}\}]/,"") }
    //     replacementsArr.each { replacement ->
    //       value = value.replaceAll(replacement.SearchText, replacement.ReplaceText)
    //     }
    //     tableTitleText = tableTitleText.replaceAll(/\{\{$name\}\}/, value)
    //   }
    // }
    // tableTitleText = tableTitleText.replaceAll("<", "&lt;").replaceAll(">", "&gt;")
    // // println tableTitleText
    //







    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);

}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
