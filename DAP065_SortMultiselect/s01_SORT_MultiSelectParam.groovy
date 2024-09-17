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

    // 1. filter on active records
    // 2. in the Db, the RowKey and RowLabels (ColumnKey, ColumnLabels) fields are lists delimited by the DBIFS
    //    - convert them into arrays
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


    // Create a map where the keys are the RowKey/ColumnKey and values are RowLabels/ColumnLabels
    // It is formed from both activeGroupByConfigsArr and activePivotedDataConfigsArr
    // Its purpose is to provide a quick reference for:
    // - matching keys with labels
    // - sorting - the list is taking from the configs in order
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

    // output this as a property so it's usable in the summary tables script
    props.setProperty("document.dynamic.userdefined.ddp_pivotConfigsKeysLabelsMapJson", prettyJson(keysLabelsMap))


    // Sort the sqlParamUserInputValues:
    // - It loops though the sqlParamUserInputValues array (which is a junction of UserInputs and sqlParamValues)
    // and compares it with the keysLabelsMap so that the items in the sqlParamUserInputValues array in 
    // in the same order as the keysLabelsMap.
    // - The complexity is when the param is a MultiSelect param (means the sql param has an IN operator
    // and so it's a list). If that's the case, the value field is a comma separated list and that list
    // needs to be sorted. We do an intersection of the two arrays. The intersection is sorted and then 
    // the value field of the sqlParamUserInputValues array item is replaced with the intersection.
    sqlParamUserInputValues.each { param ->
      if (param.MultiSelect) {

        // split the MultiSelect value (which is a comma separated list of values)
        ArrayList paramValuesArr = param.Value.split(/\s*,\s*/)
        // println paramValuesArr
        def intersection = paramValuesArr.intersect(keysLabelsMap.keySet())
        // println intersection

        if (intersection) {
          def filteredSortedParamValuesArr = intersection.sort{ m -> (keysLabelsMap.keySet() as ArrayList).indexOf(m) }
          // println filteredSortedParamValuesArr
          def filteredSortedParamDisplayValuesArr = filteredSortedParamValuesArr.collect { keysLabelsMap[it] }
          // println filteredSortedParamDisplayValuesArr

          // replace/add fields
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

    dataContext.storeStream(is, props);

}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
