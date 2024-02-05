import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()

def NEWLINE = System.lineSeparator()
// def IFS = /\|\^\|/  // Input Field Separator
// def OFS = "|^|"  // Output Field Separater
def DBIFS = "\\^\\^\\^"    // Database Field Separator
// def DBOFS = "^^^"    // Database Field Separator


for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def sqlParamUserInputValuesJson = props.getProperty("document.dynamic.userdefined.ddp_sqlParamUserInputValuesJson")
    def sqlParamUserInputValues = sqlParamUserInputValuesJson ? new JsonSlurper().parseText(sqlParamUserInputValuesJson) : []
    // println prettyJson(sqlParamUserInputValues)

    def sortedParamValuesArr = []

    def multiSelectParam = sqlParamUserInputValues.findAll { it.MultiSelect == true} ?: []
    // println multiSelectParam.Value

    if (multiSelectParam) {
      def param = multiSelectParam[0]
      def pivotKeysArr = []

      if (param.PivotConfig == "GroupBy") {
        def groupByConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_GroupByConfigsConsolidated")
        def groupByConfigsArr = groupByConfigsJson ? new JsonSlurper().parseText(groupByConfigsJson)?.Records : []
        // println prettyJson(groupByConfigsArr)
        pivotKeysArr = groupByConfigsArr.RowKey.collect { it.toUpperCase().split(DBIFS) }.transpose()
      }
      else if (param.PivotConfig == "PivotOn") {
        def pivotedDataConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_PivotedDataConfigsConsolidated")
        def pivotedDataConfigsArr = pivotedDataConfigsJson ? new JsonSlurper().parseText(pivotedDataConfigsJson)?.Records : []
        // println prettyJson(pivotedDataConfigsArr)
        pivotKeysArr = pivotedDataConfigsArr.ColumnKey.collect { it.toUpperCase().split(DBIFS) }.transpose()
      }

      if (pivotKeysArr) {
        pivotKeysArr.each {
          ArrayList paramValuesArr = multiSelectParam.Value[0].toUpperCase().split(/\s*,\s*/)
          // if (!paramValuesArr.disjoint(it)) {                                //  NO - if one param value exists in pivotKeysArr
          // if (paramValuesArr.clone().sort() == it.clone().sort()) {          //  NO - if param values and pivotKeysArr are all the same (not in same order)
          if (paramValuesArr.intersect(it).size() == paramValuesArr.size()) {   // YES - if all param values exist in pivotKeysArr (but pivotKeysArr could have more values)
            sortedParamValuesArr = paramValuesArr.sort{ m -> it.indexOf(m) } 
            // println sortedParamValuesArr
            param.Value = sortedParamValuesArr.join(", ")
            param.isSorted = true
          }
        }
      }
    }
    // println prettyJson(multiSelectParam)

    props.setProperty("document.dynamic.userdefined.ddp_sqlParamUserInputValuesJson", prettyJson(sqlParamUserInputValues))
    // println prettyJson(sqlParamUserInputValues)

    dataContext.storeStream(is, props);

}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }