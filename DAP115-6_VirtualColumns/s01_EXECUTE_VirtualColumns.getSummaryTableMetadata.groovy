import java.util.Properties;
import java.io.InputStream;
import com.boomi.execution.ExecutionUtil;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;

logger = ExecutionUtil.getBaseLogger();

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def tableInstances = new JsonSlurper().parse(is).Records
    // println prettyJson(tableInstances)
    def virtualColumnsJson = props.getProperty("document.dynamic.userdefined.ddp_VirtualColumns")
    def virtualColumns = virtualColumnsJson ? new JsonSlurper().parseText(virtualColumnsJson).Records[0] : []
    // println prettyJson(virtualColumns)
    def virtualColumnRows = virtualColumns.VirtualColumnRows
    // println prettyJson(virtualColumnRows)

    // def outData = tableInstances.collect { ti ->
    //     [
    //         "TableInstanceId": ti.TableInstanceId,
    //         "TableIdentifier": ti.UserInputsIdentifier,
    //         "Value": virtualColumnRows.find { vc -> ti.TableInstanceId == vc.TableInstanceId }?.Value ?: ""
    //     ]
    // }

    // def userInputValsArrArr = []
    def outData = []
    tableInstances.collect { ti ->

        def userInputValsArr = ti.UserInputValues.UserInputValue
        def userInputValsArrArr = userInputValsArr.collect{ it.split(/\s*,\s*/) }
        def maxArrSize = userInputValsArrArr.collect{ it.size() }.max()

        def summaryTableRowTableIdentifiersArr = []
        if (maxArrSize > 1) {
            def summaryTableRowArrArr = []
            userInputValsArrArr.each{ valsArr ->
                if (valsArr.size() == 1) {
                    def tmpValsArr = []
                    (0..maxArrSize-1).each {
                        tmpValsArr << valsArr
                    }
                    summaryTableRowArrArr << tmpValsArr.flatten()
                }
                else {
                    summaryTableRowArrArr << valsArr
                }
            }
            summaryTableRowTableIdentifiersArr = summaryTableRowArrArr.transpose().collect{ it.join(", ") }
        }
        else {
            summaryTableRowTableIdentifiersArr = userInputValsArr
        }
        // println summaryTableRowTableIdentifiersArr

        summaryTableRowTableIdentifiersArr.each { tableIdentifier ->
            outData << [
                "TableInstanceId": ti.TableInstanceId,
                "TableIdentifier": tableIdentifier,
                "Value": virtualColumnRows.find { vc -> ti.TableIdentifier == vc.TableIdentifier }?.Value ?: ""
            ]
        }

    }

    // def outData = tableInstances.collect { ti ->
    //     [
    //         "TableInstanceId": ti.TableInstanceId,
    //         "TableIdentifier": ti.UserInputsIdentifier,
    //         "Value": virtualColumnRows.find { vc -> ti.TableInstanceId == vc.TableInstanceId }?.Value ?: ""
    //     ]
    // }

    def sqlMetadataJson = props.getProperty("document.dynamic.userdefined.ddp_sqlMetadataJson")
    def sqlColumnNames = new JsonSlurper().parseText(sqlMetadataJson).Columns
    props.setProperty("document.dynamic.userdefined.ddp_sqlColumnNamesJson", JsonOutput.toJson(sqlColumnNames))


    is = new ByteArrayInputStream(prettyJson(outData).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
