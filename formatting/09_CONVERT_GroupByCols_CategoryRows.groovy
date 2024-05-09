import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
  InputStream is = dataContext.getStream(i);
  Properties props = dataContext.getProperties(i);

  /* INPUTS */
  def categoryRowPrependText = props.getProperty("document.dynamic.userdefined.ddp_categoryRowPrependText") ?: ""
  int numCategoryRows = (props.getProperty("document.dynamic.userdefined.ddp_numCategoryRows") ?: "0") as int
  numCategoryRows = 2

  /* LOGIC */
  def root = new XmlParser().parse(is)
  root.'**'.findAll() { node -> node.name() == "table" }.eachWithIndex() { table, t ->

    // when there is a rowspan, save it and then decriment it for each row that is spanned
    int rowSpanValSaved = 0
    ArrayList<Integer> rowSpansSavedArr = [0] * numCategoryRows
    // println rowSpansSavedArr

    // for each category row that is added add one to this var
    int numRowsAdded = 0

    // loop though the TRs
    ArrayList outArr = []
    Boolean firstDataRow = true
    int numCols

    table.tr.eachWithIndex { tr, f ->
      // save the node
      def rowCellsArr = tr.children()[0..numCategoryRows - 1]
      // println rowCellsArr.collect{w(25, it.value()[0])}.join("  ")

      ArrayList<Integer> rowSpansArr = rowCellsArr.collect{ it.@rowSpan ? it.@rowSpan as int : 0 }
      // println rowSpansArr


      Boolean markedForAction = false
      ArrayList markedArr = []

      
      rowCellsArr.eachWithIndex{ cell, c ->
        if (!rowSpansSavedArr[c]) {
          markedArr[c] = true
          if (rowSpansArr[c] != 0) {
            rowSpansSavedArr[c] = rowSpansArr[c] - 1
          }
        }
        else {
          rowSpansSavedArr[c]--
        }
        // if (c == 0) {
        //
        // }
      }

      println ([
        w(3, f),
        w(3, rowCellsArr[0].name()),
        // rowSpansArr.collect{w(3,it)}.join(","),
        w(7, rowSpansArr != [0,0] ? rowSpansArr.join(",") : ""),
        rowCellsArr.collect{w(30, it.value()[0])}.join(),
        w(7, rowSpansSavedArr.join(",")),
        markedArr
      ].join("  "))


      // rowSpansArr.eachWithIndex{ span, s ->
      //   if (!rowSpansSavedArr.sum()) {
      //     markedForAction = true
      //     if (span != 0) {
      //       rowSpansSavedArr[s] = span -1
      //     }
      //   } else {
      //     rowSpansSavedArr[s]--
      //   }
      // }

      // if (!rowSpanValSaved) {
      //   markedForAction = true
      //   if (rowCells_rowSpan != 0) {
      //     rowSpanValSaved = rowCells_rowSpan -1
      //   }
      // } else {
      //   rowSpanValSaved--
      // }

      // println ([
      //   w(3,  f.toString()),
      //   w(3,  rowCells.name()),
      //   w(30, rowCells.value()[0]),
      //   w(12, "rowspan: " + rowCells_rowSpan),
      //   w(10, "saved: " + rowSpanValSaved),
      //   w(7,  markedForAction ? "*MARKED*" : "")
      // ].join("  "))

      // if (markedForAction) {
      //   // delete cell
      //
      //   // if the row is not a header row (it's children are td's and not th's)
      //   // create the category row
      //   if (rowCells.name() == "td") {
      //     // determine the width of the table by getting the width of first data row
      //     // which will contain all the tds, whereas other rows might be missing
      //     // tds because they are spanned by a rowspan in a row above.
      //     if (firstDataRow) {
      //       numCols = table.tr[f].children().size()
      //       firstDataRow = false
      //     }
      //     // create new tr and append to bottom of parent table
      //     def newtr = new Node(table, 'tr')
      //     // create new td and append to new tr, add attributes
      //     def newtd = new Node(newtr, 'th', [colSpan:numCols, style:"width:100%", id:rowCells.@id] , categoryRowPrependText + (rowCells.value()[0] as String))
      //     // the best we can do above is append the new row (tr) to the bottom of the parent table
      //     // we need to basically cut (remove) it and paste (add) it at the correct row index
      //     // we need the numRowsAdded so that we don't keep pasting the new rows at the same index
      //     table.remove(newtr)
      //     table.children().add(f+numRowsAdded, newtr)
      //     numRowsAdded++
      //   }
      //
      //   // delete all the cells in the first column
      //   rowCells.replaceNode{}
      //
      // }
    }
  }

  /* OUTPUT */
  String outData
  // outData = groovy.xml.XmlUtil.serialize(root).replaceFirst("\\<\\?xml(.+?)\\?\\>", "").trim() //.replaceAll(/<tr\s*?\/\s*>/,"")
  is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
  dataContext.storeStream(is, props);
}

private String w(int width, def thing) {
  def prepend = false
  if (thing instanceof Integer) {
    prepend = true
    thing = thing.toString()
  }
  while (thing.size() < width) {
    if (prepend) thing = " " + thing
    else thing += " "
  }
  return thing
}
