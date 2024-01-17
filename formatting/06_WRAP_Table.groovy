import java.util.Properties;
import java.io.InputStream;
import groovy.xml.XmlUtil
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import groovy.xml.StreamingMarkupBuilder;
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()
def IFS = /\|\^\|/  // Input Field Separater

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def activePivotedDataConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_PivotedDataConfigsConsolidated")
    def activePivotedDataConfigsArr = activePivotedDataConfigsJson ? new JsonSlurper().parseText(activePivotedDataConfigsJson)?.Records : []
    // println prettyJson(activePivotedDataConfigsArr)
    // println activePivotedDataConfigsArr.size()

    Boolean isPivot = (props.getProperty("document.dynamic.userdefined.ddp_isPivot") ?: "true").toBoolean()
    // println "#DEBUG isPivot: " + isPivot

    Boolean displayHeadersOnSide = (props.getProperty("document.dynamic.userdefined.ddp_displayHeadersOnSide") ?: "true").toBoolean()
    // println "displayHeadersOnSide: " + displayHeadersOnSide

    int numHeaderCols = (props.getProperty("document.dynamic.userdefined.ddp_numHeaderCols") ?: "1") as int
    // println "numHeaderCols: " + numHeaderCols



    /* LOGIC */

    // renumber indices so they are offset by headers cols
    activePivotedDataConfigsArr.eachWithIndex { item, c ->
        item.ColumnIndex = numHeaderCols + c
    }
    // println "#DEBUG activePivotedDataConfigsArr: " + prettyJson(activePivotedDataConfigsArr)

    if (isPivot) {

        def tableGroup = new XmlParser().parseText(is.text.replaceFirst(/(?i)(<\/tablegroup>).*$/,"\$1"))

        int numSubTables = activePivotedDataConfigsArr.SubTableIndex.max()

        if (numSubTables > 1) {

            def table = tableGroup.table[0]
            // println table.tr[4].collect{ it.value() }[0]

            (1..numSubTables).each { subTableIndex ->
                // println "\n::: SubTableIndex: " + subTableIndex + " :::\n"
                // activePivotedDataConfigsArr.findAll { it.SubTableIndex == subTableIndex }.eachWithIndex { item, j -> println j + ": " + item.ColumnIndex + ": " + item.ColumnKey + ": " + tableGroup.table.tr[0].children()[item.ColumnIndex]}

                def subTableConfigArr = activePivotedDataConfigsArr.findAll { it.SubTableIndex == subTableIndex }

                def newTableNode = new NodeBuilder().table(table.attributes().clone())
                tableGroup.append(newTableNode)

                newTableNode.@id = newTableNode.@id + subTableIndex
                newTableNode.@seq = subTableIndex
                newTableNode.@pagebreak = subTableIndex == 1 ? true : false

                table.tr.each{ tr ->
                    // println "--- TR ---"
                    def rowHeaderNodeArr = tr.children()[0..numHeaderCols-1]
                    // println "#DEBUG rowHeaderNodeArr: " + rowHeaderNodeArr.collect{ it.value()}

                    def newTrNode = new NodeBuilder().tr() {

                        if (displayHeadersOnSide) {
                            rowHeaderNodeArr.collect { rowHeaderNode ->
                                "${rowHeaderNode.name()}"(rowHeaderNode.attributes().clone(), "${rowHeaderNode.text()}")
                                rowHeaderNode.@id += subTableIndex
                            }
                        }

                        subTableConfigArr.eachWithIndex { item, j ->
                            // println j + " " + item.ColumnIndex.toString()
                            def childNode = tr.children()[item.ColumnIndex]
                            // println childNode
                            "${childNode.name()}"(childNode.attributes(), "${childNode.text()}")
                        }
                    }
                    newTableNode.append(newTrNode)
                }
            }

            table.replaceNode{}

            // println XmlUtil.serialize(tableGroup).replaceFirst("\\<\\?xml(.+?)\\?\\>", "").trim()
        }

        def outData = groovy.xml.XmlUtil.serialize(tableGroup).replaceFirst("\\<\\?xml(.+?)\\?\\>", "").trim()
        // def outData = "-"
        is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    }

    dataContext.storeStream(is, props);
}
 
private static String prettyJson(def thing) {
    return JsonOutput.prettyPrint(JsonOutput.toJson(thing))
}

