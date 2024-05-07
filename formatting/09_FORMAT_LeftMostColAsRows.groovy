/* In pivoted tables, the left column or columns are the GroupBy columns. They are
 * like the category label for their row. Sometimes the user wants to put that category
 * label above their rows instead of on the side. This script allows that for the first
 * GroupBy column on the left. 
 *
 * Example:
 *
 *     this table
 *
 *         +----------+--------------+----+----+----+----+
 *         | Category | Sub-category | t1 | t2 | t3 | t4 |
 *         +----------+--------------+----+----+----+----+
 *         | First    | a            | x  | x  | x  | x  |
 *         +----------+--------------+----+----+----+----+
 *         | First    | b            | x  | x  | x  | x  |
 *         +----------+--------------+----+----+----+----+
 *         | Second   | a            | x  | x  | x  | x  |
 *         +----------+--------------+----+----+----+----+
 *         | Second   | b            | x  | x  | x  | x  |
 *         +----------+--------------+----+----+----+----+
 *         | Second   | c            | x  | x  | x  | x  |
 *         +----------+--------------+----+----+----+----+
 *
 *     becomes
 *
 *           +--------------+----+----+----+----+
 *           | Sub-category | t1 | t2 | t3 | t4 |
 *           +--------------+----+----+----+----+
 *           |               First              |
 *           +--------------+----+----+----+----+
 *           | a            | x  | x  | x  | x  |
 *           +--------------+----+----+----+----+
 *           | b            | x  | x  | x  | x  |
 *           +--------------+----+----+----+----+
 *           |              Second              |
 *           +--------------+----+----+----+----+
 *           | a            | x  | x  | x  | x  |
 *           +--------------+----+----+----+----+
 *           | b            | x  | x  | x  | x  |
 *           +--------------+----+----+----+----+
 *           | c            | x  | x  | x  | x  |
 *           +--------------+----+----+----+----+
 *
*/

import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
  InputStream is = dataContext.getStream(i);
  Properties props = dataContext.getProperties(i);

  /* INPUTS */
  def categoryRowPrependText = props.getProperty("document.dynamic.userdefined.ddp_categoryRowPrependText") ?: ""

  /* LOGIC */
  def root = new XmlParser().parse(is)
  root.'**'.findAll() { node -> node.name() == "table" }.eachWithIndex() { table, t ->

    // when there is a rowspan, save it and then decriment it for each row that is spanned
    int rowSpanValSaved = 0
    // for each category row that is added add one to this var
    int numRowsAdded = 0

    // loop though the TRs
    Boolean firstDataRow = true
    int numCols
    table.tr.eachWithIndex { tr, f ->
      // save the node
      def firstCellInRow = tr.children()[0]
      // println firstCellInRow
      int firstCellInRow_rowSpan = firstCellInRow.@rowSpan ? firstCellInRow.@rowSpan as int : 0
      // println firstCellInRow_rowSpan
      // def numSpan = firstCellInRow.parent().children().@rowSpan
      // // println numCols + " " + numSpan

      // figure out which rows need action taken, mark the cell if:
      // - the first col has a rowspan 
      // - the row is within the span of a first col rowspan
      // - the first col does not have a rowspan and is not within
      //   the span of a first col rowspan
      // save the rowspan or decriment the saved one
      Boolean markedForAction = false
      // if (!rowSpanValSaved && firstCellInRow_rowSpan != 0) {
      //   markedForAction = true
      //   rowSpanValSaved = firstCellInRow_rowSpan -1
      // } else if (!rowSpanValSaved) {
      //   markedForAction = true
      // } else if (rowSpanValSaved) {
      //   rowSpanValSaved--
      // }
      if (!rowSpanValSaved) {
        markedForAction = true
        if (firstCellInRow_rowSpan != 0) {
          rowSpanValSaved = firstCellInRow_rowSpan -1
        }
      } else {
        rowSpanValSaved--
      }

      // println ([
      //   w(3,  f.toString()),
      //   w(3,  firstCellInRow.name()),
      //   w(30, firstCellInRow.value()[0]),
      //   w(12, "rowspan: " + firstCellInRow_rowSpan),
      //   w(10, "saved: " + rowSpanValSaved),
      //   w(7,  markedForAction ? "*MARKED*" : "")
      // ].join("  "))

      if (markedForAction) {
        // delete cell

        // if the row is not a header row (it's children are td's and not th's)
        // create the category row
        if (firstCellInRow.name() == "td") {
          // determine the width of the table by getting the width of first data row
          // which will contain all the tds, whereas other rows might be missing
          // tds because they are spanned by a rowspan in a row above.
          if (firstDataRow) {
            numCols = table.tr[f].children().size()
            firstDataRow = false
          }
          // create new tr and append to bottom of parent table
          def newtr = new Node(table, 'tr')
          // create new td and append to new tr, add attributes
          def newtd = new Node(newtr, 'th', [colSpan:numCols, style:firstCellInRow.@style, id:firstCellInRow.@id] , categoryRowPrependText + (firstCellInRow.value()[0] as String))
          // the best we can do above is append the new row (tr) to the bottom of the parent table
          // we need to basically cut (remove) it and paste (add) it at the correct row index
          // we need the numRowsAdded so that we don't keep pasting the new rows at the same index
          table.remove(newtr)
          table.children().add(f+numRowsAdded, newtr)
          numRowsAdded++
        }

        // delete all the cells in the first column
        firstCellInRow.replaceNode{}

      }
    }
  }

  /* OUTPUT */
  String outData
  outData = groovy.xml.XmlUtil.serialize(root).replaceFirst("\\<\\?xml(.+?)\\?\\>", "").trim() //.replaceAll(/<tr\s*?\/\s*>/,"")
  is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
  dataContext.storeStream(is, props);
}

private String w(int width, String str) { while (str.size() < width) str += " " ; return str }
