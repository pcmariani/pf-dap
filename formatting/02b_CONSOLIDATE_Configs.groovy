import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;
logger = ExecutionUtil.getBaseLogger()

def NEWLINE = System.lineSeparator()
def IFS = /\|\^\|/  // Input Field Separator
def OFS = "|^|"     // Output Field Separator

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    /* INPUTS */

    // existing configs
    def pivotedDataConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_PivotedDataConfigs")
    def pivotedDataConfigsArr = pivotedDataConfigsJson ? new JsonSlurper().parseText(pivotedDataConfigsJson).Records : []
    // println prettyJson(pivotedDataConfigsArr)
    // println pivotedDataConfigsArr.size()
    def groupByConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_GroupByConfigs")
    def groupByConfigsArr = groupByConfigsJson ? new JsonSlurper().parseText(groupByConfigsJson).Records : []
    // println prettyJson(groupByConfigsArr)
    // println groupByConfigsArr.size()

    // new configs
    def newPivotedDataConfigs = props.getProperty("document.dynamic.userdefined.ddp_NewPivotedDataConfigs")
    def newPivotedDataConfigsArr = newPivotedDataConfigs ? new JsonSlurper().parseText(newPivotedDataConfigs).Records : []
    // println prettyJson(newPivotedDataConfigsArr)
    // println newPivotedDataConfigsArr.size()
    def newGroupByConfigs = props.getProperty("document.dynamic.userdefined.ddp_NewGroupByConfigs")
    def newGroupByConfigsArr = newGroupByConfigs ? new JsonSlurper().parseText(newGroupByConfigs).Records : []
    // println prettyJson(newGroupByConfigsArr)
    // println newGroupByConfigsArr.size()

    // for wrapping
    def requestor = props.getProperty("document.dynamic.userdefined.ddp_Requestor")
    def reportContentItem_DynamicTableId = props.getProperty("document.dynamic.userdefined.ddp_ReportContentItem_DynamicTableId")
    def tableDefinitionId = props.getProperty("document.dynamic.userdefined.ddp_TableDefinitionId")
    def sourceSqlQueryId = props.getProperty("document.dynamic.userdefined.ddp_SourceSqlQueryId")



    /* LOGIC */

    def pivotedDataConfigsConsolidated = pivotedDataConfigsArr + newPivotedDataConfigsArr
    // println prettyJson(pivotedDataConfigsConsolidated)
    // println pivotedDataConfigsConsolidated.size()

    def groupByConfigsConsolidated = groupByConfigsArr + newGroupByConfigsArr
    // println prettyJson(groupByConfigsConsolidated)
    // println groupByConfigsConsolidated.size()



    /* OUTPUT */

    // consolidated configs
    props.setProperty("document.dynamic.userdefined.ddp_PivotedDataConfigs", "***cleared***")
    props.setProperty("document.dynamic.userdefined.ddp_GroupByConfigs", "***cleared***")
    props.setProperty("document.dynamic.userdefined.ddp_NewPivotedDataConfigs", "***cleared***")
    props.setProperty("document.dynamic.userdefined.ddp_NewGroupByConfigs", "***cleared***")
    props.setProperty("document.dynamic.userdefined.ddp_GroupByConfigsConsolidated", prettyJson([Records:groupByConfigsConsolidated]))
    props.setProperty("document.dynamic.userdefined.ddp_PivotedDataConfigsConsolidated", prettyJson([Records:pivotedDataConfigsConsolidated]))

    dataContext.storeStream(is, props);
}


private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
