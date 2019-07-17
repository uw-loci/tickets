// MakeQT.java

import java.awt.image.BufferedImage;
import java.io.IOException;
import loci.formats.*;
import loci.formats.out.QTWriter;

public class MakeQT {
  public static void main(String[] args) throws FormatException, IOException {
    String inId = args[0], outId = args[1];
    ImageReader r = new ImageReader();
    r.setId(inId);
    int imageCount = r.getImageCount();
    QTWriter w = new QTWriter();
    w.setCodec(1835692130); // Motion JPEG-B
    w.setId(outId);
    for (int i=0; i<imageCount; i++) {
      BufferedImage img = r.openImage(i);
      w.saveImage(img, i == imageCount - 1);
    }
    w.close();
    r.close();
  }
}
