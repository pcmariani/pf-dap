import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def root = new XmlSlurper().parse(is)
    def colspanRange = props.getProperty("document.dynamic.userdefined.ddp_colspanRange")
    
    root.'**'.findAll() { node -> node.name() == "table" }.eachWithIndex() { table, e ->
        def (rowStart, rowEnd, colStart, colEnd) = colspanRange.split(/\s*,\s*/).collect{it as int}
    
        rowEnd = getEndIndex(table.tr.size(), rowEnd)
        colEnd = getEndIndex(table.tr[0].children().size(), colEnd)
        
        def rowSpanArr = getColSpanAndDeletionArr(table, rowStart, rowEnd, colStart, colEnd)

        table.tr[rowStart..rowEnd].eachWithIndex() { tr, r ->
          tr.children()[colStart..colEnd].reverse().eachWithIndex() { it, s ->
            if (rowSpanArr[r][s] == "DELETE") {
              it.@delete = "TRUE"
            }
            else if (rowSpanArr[r][s] != null) {
              it.@colSpan = rowSpanArr[r][s].toString()
            }
          }
        }
        
        table.'**'.findAll{ it.@delete == "TRUE" }.each { it.replaceNode{} }
    }
    
    def outData = groovy.xml.XmlUtil.serialize(root).replaceAll("\\<\\?xml(.+?)\\?\\>", "").trim()

    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}


def getColSpanAndDeletionArr(table, rowStart, rowEnd, colStart, colEnd) {
    def combinedArr = []
    table.tr[rowStart..rowEnd].each() { tr ->
        // println tr
        def numCols = colEnd - colStart + 1
        def tdPrev
        def countArr = [0]*numCols
        def delArr = [null]*numCols
        tr.children()[colStart..colEnd].reverse().eachWithIndex() { it, s ->

            // if (it == tdPrev && !it.attributes().rowSpan) {
            if (it == tdPrev) {
                println it.text() + ": " + it.attributes().rowSpan
                countArr[s] = countArr[s-1] + 1
                delArr[s-1] = "DELETE"
            }
            tdPrev = it
        }
        def combinedRowArr = []
        delArr.eachWithIndex { col, c ->
            combinedRowArr[c] = col == "DELETE" ? col : ( countArr[c] > 0 ? countArr[c]+1 : null)
        }
        combinedArr << combinedRowArr
    }
    // println combinedArr
    return combinedArr
}


def getEndIndex(numItems, endIndex) {
  if (endIndex == -1) return numItems - 1
  else return endIndex
}
