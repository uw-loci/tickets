import loci.formats.*;
import loci.formats.meta.*;
import ome.xml.model.primitives.*;

public class OverwriteTest {
  public static void main(String[] args) throws Exception {
    ImageReader r = new ImageReader();
    IMetadata omeMeta = MetadataTools.createOMEXMLMetadata();
    r.setMetadataStore(omeMeta);
    r.setId(args[0]);

    ImageWriter w = new ImageWriter();
    w.setInterleaved(r.isInterleaved());
    w.setMetadataRetrieve(omeMeta);
    w.setId("overwrite.ome.tiff");

    for (int i=0; i<r.getImageCount(); i++) {
      w.saveBytes(i, r.openBytes(i));
    }
    w.close();

    omeMeta.setPixelsBinDataBigEndian(true, 0, 0);
    omeMeta.setPixelsSizeT(new PositiveInteger(r.getImageCount() + 1), 0);
    w.setMetadataRetrieve(omeMeta);

    w.setId("overwrite.ome.tiff");
    w.saveBytes(r.getImageCount(), r.openBytes(r.getImageCount() - 1));
    w.close();

    r.close();
  }
}
