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

    def pivotedDataConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_PivotedDataConfigsConsolidated")
    def pivotedDataConfigsArr = pivotedDataConfigsJson ? new JsonSlurper().parseText(pivotedDataConfigsJson)?.Records : []
    // println prettyJson(pivotedDataConfigsArr)

    def groupByConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_GroupByConfigsConsolidated")
    def groupByConfigsArr = groupByConfigsJson ? new JsonSlurper().parseText(groupByConfigsJson)?.Records : []
    // println prettyJson(groupByConfigsArr)


    def activeGroupByConfigsArr = groupByConfigsArr.findAll { it.Active == true }.eachWithIndex { item, r ->
        item.RowKey = item.RowKey.split(DBIFS) as ArrayList
        item.RowLabels = item.RowLabels.split(DBIFS) as ArrayList
    }
    def activePivotedDataConfigsArr = pivotedDataConfigsArr.findAll { it.Active == true }.eachWithIndex { item, c ->
        item.ColumnKey = item.ColumnKey.split(DBIFS) as ArrayList
        item.ColumnLabels = item.ColumnLabels.split(DBIFS) as ArrayList
    }
    // println "#DEBUG activeGroupByConfigsArr: " + prettyJson(activeGroupByConfigsArr)
    // println "#DEBUG activePivotedDataConfigsArr: " + prettyJson(activePivotedDataConfigsArr)


    def keysLabelsMap = [:]

    activeGroupByConfigsArr.RowKey.eachWithIndex { keysArr, keysArrIndex ->
      keysArr.eachWithIndex { key, keyIndex ->
        if (key) {
          def label = activeGroupByConfigsArr.RowLabels[keysArrIndex][keyIndex]
          keysLabelsMap[key] = label
        }
      }
    }
    activePivotedDataConfigsArr.ColumnKey.eachWithIndex { keysArr, keysArrIndex ->
      keysArr.eachWithIndex { key, keyIndex ->
        if (key) {
          def label = activePivotedDataConfigsArr.ColumnLabels[keysArrIndex][keyIndex]
          keysLabelsMap[key] = label
        }
      }
    }
    // println prettyJson(keysLabelsMap)

    props.setProperty("document.dynamic.userdefined.ddp_pivotConfigsKeysLabelsMapJson", prettyJson(keysLabelsMap))


    sqlParamUserInputValues.each { param ->
      if (param.MultiSelect) {

        ArrayList paramValuesArr = param.Value.split(/\s*,\s*/)
        // println paramValuesArr
        ArrayList keysLabelsMapKeySet = keysLabelsMap.keySet()
        // println keysLabelsMapKeySet
        def intersection = paramValuesArr.intersect(keysLabelsMapKeySet)
        // println intersection

        if (intersection) {
          def filteredSortedParamValuesArr = intersection.sort{ m -> keysLabelsMapKeySet.indexOf(m) }
          // println filteredSortedParamValuesArr
          def filteredSortedParamDisplayValuesArr = filteredSortedParamValuesArr.collect { keysLabelsMap[it] }
          // println filteredSortedParamDisplayValuesArr

          param.Value = filteredSortedParamValuesArr.join(", ")
          param.isSorted = true
          param.DisplayValue = filteredSortedParamDisplayValuesArr.join(", ")
        }

        else {
          param.isSorted = true
          param.DisplayValue = param.Value

        }
      }

      else {
        def displayValue = keysLabelsMap[param.Value]
        if (displayValue) {
          param.DisplayValue = displayValue
        } else {
          param.DisplayValue = param.Value
        }
      }
    }

    props.setProperty("document.dynamic.userdefined.ddp_sqlParamUserInputValuesJson", prettyJson(sqlParamUserInputValues))


    //
    //
    // def filteredSortedParamValuesArr = []
    //
    // def multiSelectParam = sqlParamUserInputValues.findAll { it.MultiSelect == true} ?: []
    // // println multiSelectParam.Value
    //
    // if (multiSelectParam) {
    //   def param = multiSelectParam[0]
    //   ArrayList pivotKeysArr = []
    //   ArrayList activePivotKeysArrsArr = []
    //
    //   if (param.PivotConfig == "GroupBy") {
    //     def groupByConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_GroupByConfigsConsolidated")
    //     def groupByConfigsArr = groupByConfigsJson ? new JsonSlurper().parseText(groupByConfigsJson)?.Records : []
    //     // println prettyJson(groupByConfigsArr)
    //     activePivotKeysArrsArr = groupByConfigsArr.findAll{it.Active == true}.RowKey.collect { it.toUpperCase().split(DBIFS) }.transpose()
    //   }
    //   else if (param.PivotConfig == "PivotOn") {
    //     def pivotedDataConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_PivotedDataConfigsConsolidated")
    //     def pivotedDataConfigsArr = pivotedDataConfigsJson ? new JsonSlurper().parseText(pivotedDataConfigsJson)?.Records : []
    //     // println prettyJson(pivotedDataConfigsArr)
    //     activePivotKeysArrsArr = pivotedDataConfigsArr.findAll{it.Active == true}.ColumnKey.collect{ it.toUpperCase().split(DBIFS).findAll{ it!=""} }.transpose()
    //   }
    //   // println activePivotKeysArrsArr
    //
    //   if (activePivotKeysArrsArr) {
    //     activePivotKeysArrsArr.each { activePivotKeysArr ->
    //       // println activePivotKeysArr
    //       ArrayList paramValuesArr = multiSelectParam.Value[0].toUpperCase().split(/\s*,\s*/)
    //       // println paramValuesArr
    //       def intersection = paramValuesArr.intersect(activePivotKeysArr)
    //       // println intersection
    //       if (intersection) {
    //         param.Value = intersection.sort{ m -> activePivotKeysArr.indexOf(m) }.join(", ")
    //         // println param.Value
    //         param.isSorted = true
    //       }
    //
    //     }
    //   }
    // }
    // // println prettyJson(multiSelectParam)
    //
    // // println prettyJson(sqlParamUserInputValues)
    //
    dataContext.storeStream(is, props);

}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
