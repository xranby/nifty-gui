package de.lessvoid.nifty.renderer.lwjgl2.render;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL31.GL_PRIMITIVE_RESTART;
import static org.lwjgl.opengl.GL31.glPrimitiveRestartIndex;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jglfont.BitmapFontException;
import org.jglfont.BitmapFontFactory;
import org.jglfont.spi.BitmapFontRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Cursor;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Matrix4f;

import de.lessvoid.coregl.CoreElementVBO;
import de.lessvoid.coregl.CoreMatrixFactory;
import de.lessvoid.coregl.CoreRender;
import de.lessvoid.coregl.CoreShader;
import de.lessvoid.coregl.CoreTexture2D;
import de.lessvoid.coregl.CoreTexture2D.ColorFormat;
import de.lessvoid.coregl.CoreTexture2D.ResizeFilter;
import de.lessvoid.coregl.CoreTextureAtlasGenerator;
import de.lessvoid.coregl.CoreVAO;
import de.lessvoid.coregl.CoreVBO;
import de.lessvoid.nifty.render.BlendMode;
import de.lessvoid.nifty.renderer.lwjgl2.render.io.ImageData;
import de.lessvoid.nifty.renderer.lwjgl2.render.io.ImageIOImageData;
import de.lessvoid.nifty.renderer.lwjgl2.render.io.TGAImageData;
import de.lessvoid.nifty.spi.render.MouseCursor;
import de.lessvoid.nifty.spi.render.RenderDevice;
import de.lessvoid.nifty.spi.render.RenderFont;
import de.lessvoid.nifty.spi.render.RenderImage;
import de.lessvoid.nifty.tools.Color;
import de.lessvoid.nifty.tools.ObjectPool;
import de.lessvoid.nifty.tools.ObjectPool.Factory;
import de.lessvoid.nifty.tools.resourceloader.NiftyResourceLoader;
import de.lessvoid.resourceloader.ResourceLoader;
import de.lessvoid.simpleimageloader.SimpleImageLoader;
import de.lessvoid.simpleimageloader.SimpleImageLoaderConfig;
import de.lessvoid.textureatlas.TextureAtlasGenerator.Result;

/**
 * Lwjgl RenderDevice Implementation.
 * @author void
 */
public class LwjglRenderDevice2 implements RenderDevice {
  private static final int PRIMITIVE_SIZE = 4*8; // 4 vertices per quad and 12 vertex attributes per vertex (2xpos, 2xtexture, 4xcolor, 4xclipping)
  private static final int PRIMITIVE_RESTART_INDEX = 0xFFFF;

  private static Logger log = Logger.getLogger(LwjglRenderDevice2.class.getName());
  private static IntBuffer viewportBuffer = BufferUtils.createIntBuffer(4 * 4);
  private NiftyResourceLoader resourceLoader;
  private int viewportWidth = -1;
  private int viewportHeight = -1;
  private long time;
  private long frames;
  private long lastFrames;
  private boolean displayFPS = false;
  private boolean logFPS = false;
  private RenderFont fpsFont;

  private BlendMode currentBlendMode = null;
  private boolean currentClipping = false;
  private int currentClippingX0 = 0;
  private int currentClippingY0 = 0;
  private int currentClippingX1 = 0;
  private int currentClippingY1 = 0;

  private StringBuilder buffer = new StringBuilder();
  private int glyphCount;

  private final CoreShader niftyShader;
  private final Matrix4f modelViewProjection;
  private CoreTextureAtlasGenerator generator;
  private final LwjglRenderImage2 plainImage;
  private Batch currentBatch;
  private final List<Batch> batches = new ArrayList<Batch>();
  private final ObjectPool<Batch> batchPool;
  private final ResourceLoader resourceLoader2 = new ResourceLoader();
  private int completeClippedCounter;

  private final BitmapFontFactory factory;
  private final Color textColor = new Color(0.f, 0.f, 0.f, 0.f);
  private float[] primitiveBuffer = new float[PRIMITIVE_SIZE];
  private int[] elementIndexBuffer = new int[5];

  /**
   * The standard constructor. You'll use this in production code. Using this
   * constructor will configure the RenderDevice to not log FPS on System.out.
   */
  public LwjglRenderDevice2() {
    time = System.currentTimeMillis();
    frames = 0;

    modelViewProjection = CoreMatrixFactory.createOrtho(0, getWidth(), getHeight(), 0);

    niftyShader = CoreShader.newShaderWithVertexAttributes("aVertex", "aColor", "aTexture"/*, "aClipping"*/);
    niftyShader.fragmentShader("nifty.fs");
    niftyShader.vertexShader("nifty.vs");
    niftyShader.link();
    niftyShader.activate();
    niftyShader.setUniformMatrix4f("uModelViewProjectionMatrix", modelViewProjection);
    niftyShader.setUniformi("uTex", 0);

    final BatchMode batchMode = new BatchModeMapBuffer();
    batchPool = new ObjectPool<Batch>(2, new Factory<Batch>() {
      @Override
      public Batch createNew() {
        return new Batch(batchMode);
      }
    });

    generator = new CoreTextureAtlasGenerator(2048, 2048);
    plainImage = (LwjglRenderImage2) createImage("nifty.tga", false);

    niftyShader.activate();
    factory = new BitmapFontFactory(new FontRenderer(generator));
  }

  /**
   * The development mode constructor allows to display the FPS on screen when
   * the given flag is set to true. Note that setting displayFPS to false will
   * still log the FPS on System.out every couple of frames.
   * @param displayFPS
   */
  public LwjglRenderDevice2(final boolean displayFPS) {
    this();
    this.logFPS = true;
    this.displayFPS = displayFPS;
  }

  @Override
  public void setResourceLoader(final NiftyResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;

    if (this.displayFPS) {
      fpsFont = createFont("fps.fnt");
    }
  }

  /**
   * Get Width.
   * @return width of display mode
   */
  public int getWidth() {
    if (viewportWidth == -1) {
      getViewport();
    }
    return viewportWidth;
  }

  /**
   * Get Height.
   * @return height of display mode
   */
  public int getHeight() {
    if (viewportHeight == -1) {
      getViewport();
    }
    return viewportHeight;
  }

  private void getViewport() {
    GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
    viewportWidth = viewportBuffer.get(2);
    viewportHeight = viewportBuffer.get(3);
    if (log.isLoggable(Level.FINEST)) {
      log.finest("Viewport: " + viewportWidth + ", " + viewportHeight);
    }
  }

  public void beginFrame() {
    log.finest("beginFrame()");

    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    currentBlendMode = BlendMode.BLEND;

    //GL11.glDisable(GL11.GL_SCISSOR_TEST);
    currentClipping = false;
    currentClippingX0 = 0;
    currentClippingY0 = 0;
    currentClippingX1 = getWidth();
    currentClippingY1 = getHeight();
    completeClippedCounter = 0;

    for (int i=0; i<batches.size(); i++) {
      batchPool.free(batches.get(i));
    }
    batches.clear();
    batches.add(batchPool.allocate());
    currentBatch = batches.get(0);
    currentBatch.begin();

    generator.getTargetTexture().bindTexture();
    glyphCount = 0;
  }

  public void endFrame() {
    log.finest("endFrame");
    log.fine("completely clipped elements: " + completeClippedCounter);

    if (displayFPS) {
      renderFont(fpsFont, buffer.toString(), 10, getHeight() - fpsFont.getHeight() - 10, Color.RED, 1.0f, 1.0f);
    }

    niftyShader.activate();
    currentBatch.end();

    glEnable(GL_PRIMITIVE_RESTART);
    glPrimitiveRestartIndex(PRIMITIVE_RESTART_INDEX);

    int totalPrimitiveCount = 0;
    for (int i=0; i<batches.size(); i++) {
      Batch batch = batches.get(i);

      int primitiveCount = batch.getPrimitiveCount();
      batch.bind();
      CoreRender.renderTriangleStripIndexed(batch.getIndexCount());

      totalPrimitiveCount += primitiveCount;
    }

    glDisable(GL_PRIMITIVE_RESTART);

    frames++;
    long diff = System.currentTimeMillis() - time;
    if (diff >= 1000) {
      time += diff;
      lastFrames = frames;

      buffer.setLength(0);
      buffer.append("FPS: ");
      buffer.append(lastFrames);
      buffer.append(", Triangles: ");
      buffer.append(totalPrimitiveCount*2);
      buffer.append(", Vertices: ");
      buffer.append(totalPrimitiveCount*4);
      buffer.append(", Glyph: ");
      buffer.append(glyphCount);
      buffer.append(", VBO-Size (sum): ");
      buffer.append(totalPrimitiveCount*PRIMITIVE_SIZE*4);
      buffer.append(" b");
      buffer.append(", Batches: ");
      buffer.append(batches.size());

      if (logFPS) {
        System.out.println(buffer.toString());
      }

      frames = 0;
    }

    // currently the RenderDevice interface does not support a way to be notified when the resolution is changed
    // so we reset the viewportWidth and viewportHeight here so that we only call getViewport() once per frame and
    // not each time someone calls getWidth() or getHeight().
    viewportWidth = -1;
    viewportHeight = -1;

    checkGLError();
  }

  public void clear() {
    log.finest("clear()");

    GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
  }

  public RenderImage createImage(final String filename, final boolean filterLinear) {
    // we need to disable blending in here to get the original image into the texture atlas without any blending
    glDisable(GL_BLEND);
    LwjglRenderImage2 result = new LwjglRenderImage2(generator, filename, filterLinear, resourceLoader2);
    glEnable(GL_BLEND);
    return result;
  }

  public RenderFont createFont(final String filename) {
    try {
      // we need to disable blending in here to get the original image into the texture atlas without any blending
      glDisable(GL_BLEND);
      return new LwjglRenderFont2(filename, factory, resourceLoader);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      glEnable(GL_BLEND);
    }
  }

  public void renderQuad(final int x, final int y, final int width, final int height, final Color color) {
    log.finest("renderQuad()");
    addQuad(x, y, width, height, color, color, color, color, plainImage.getX(), plainImage.getY(), plainImage.getWidth(), plainImage.getHeight());
  }

  public void renderQuad(final int x, final int y, final int width, final int height, final Color topLeft, final Color topRight, final Color bottomRight, final Color bottomLeft) {
    log.finest("renderQuad2()");
    addQuad(x, y, width, height, topLeft, topRight, bottomLeft, bottomRight, plainImage.getX(), plainImage.getY(), plainImage.getWidth(), plainImage.getHeight());
  }

  public void renderImage(final RenderImage image, final int x, final int y, final int width, final int height, final Color c, final float scale) {
    log.finest("renderImage()");

    if (width < 0) {
      log.warning("Attempt to render image with negative width");
      return;
    }
    if (height < 0) {
      log.warning("Attempt to render image with negative height");
      return;
    }

    LwjglRenderImage2 img = (LwjglRenderImage2) image;
    float centerX = x + width / 2.f;
    float centerY = y + height / 2.f;
    int ix = Math.round(centerX - (width * scale) / 2.f);
    int iy = Math.round(centerY - (height * scale) / 2.f);
    int iw = Math.round(width * scale);
    int ih = Math.round(height * scale);
    addQuad(ix, iy, iw, ih, c, c, c, c, img.getX(), img.getY(), img.getWidth(), img.getHeight());
  }

  public void renderImage(
      final RenderImage image,
      final int x,
      final int y,
      final int w,
      final int h,
      final int srcX,
      final int srcY,
      final int srcW,
      final int srcH,
      final Color c,
      final float scale,
      final int centerX,
      final int centerY) {
    log.finest("renderImage2()");

    if (w < 0) {
      log.warning("Attempt to render image with negative width");
      return;
    }
    if (h < 0) {
      log.warning("Attempt to render image with negative height");
      return;
    }

    int ix = Math.round(-scale * centerX + scale * x + centerX);
    int iy = Math.round(-scale * centerY + scale * y + centerY);
    int iw = Math.round(w * scale);
    int ih = Math.round(h * scale);

    LwjglRenderImage2 img = (LwjglRenderImage2) image;
    addQuad(ix, iy, iw, ih, c, c, c, c, img.getX() + srcX, img.getY() + srcY, srcW, srcH);
  }

  public void renderFont(final RenderFont font, final String text, final int x, final int y, final Color color, final float sizeX, final float sizeY) {
    log.finest("renderFont()");

    LwjglRenderFont2 renderFont = (LwjglRenderFont2) font;
    renderFont.getBitmapFont().renderText(x, y, text, sizeX, sizeY, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
  }

  public void enableClip(final int x0, final int y0, final int x1, final int y1) {
    log.finest("enableClip()");

    if (currentClipping && currentClippingX0 == x0 && currentClippingY0 == y0 && currentClippingX1 == x1 && currentClippingY1 == y1) {
      return;
    }
    currentClipping = true;
    currentClippingX0 = x0;
    currentClippingY0 = y0;
    currentClippingX1 = x1;
    currentClippingY1 = y1;
  }

  public void disableClip() {
    log.finest("disableClip()");

    if (!currentClipping) {
      return;
    }
    currentClipping = false;
    currentClippingX0 = 0;
    currentClippingY0 = 0;
    currentClippingX1 = getWidth();
    currentClippingY1 = getHeight();
  }

  public void setBlendMode(final BlendMode renderMode) {
    log.finest("setBlendMode()");

    if (renderMode.equals(currentBlendMode)) {
      return;
    }

    currentBlendMode = renderMode;
    if (currentBlendMode.equals(BlendMode.BLEND)) {
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    } else if (currentBlendMode.equals(BlendMode.MULIPLY)) {
      GL11.glBlendFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO);
    }
    addNewBatch();
  }

  public MouseCursor createMouseCursor(final String filename, final int hotspotX, final int hotspotY) throws IOException {
    return new LwjglMouseCursor(loadMouseCursor(filename, hotspotX, hotspotY));
  }

  public void enableMouseCursor(final MouseCursor mouseCursor) {
    Cursor nativeCursor = null;
    if (mouseCursor != null) {
      nativeCursor = ((LwjglMouseCursor) mouseCursor).getCursor(); 
    }
    try {
      Mouse.setNativeCursor(nativeCursor);
    } catch (LWJGLException e) {
      log.warning(e.getMessage());
    }
  }

  public void disableMouseCursor() {
    try {
      Mouse.setNativeCursor(null);
    } catch (LWJGLException e) {
      log.warning(e.getMessage());
    }
  }

  private Cursor loadMouseCursor(final String name, final int hotspotX, final int hotspotY) throws IOException {
    ImageData imageLoader = createImageLoader(name);
    ByteBuffer imageData = imageLoader.loadMouseCursorImage(resourceLoader.getResourceAsStream(name));
    imageData.rewind();
    int width = imageLoader.getWidth();
    int height = imageLoader.getHeight();
    try {
      return new Cursor(width, height, hotspotX, height - hotspotY - 1, 1, imageData.asIntBuffer(), null);
    } catch (LWJGLException e) {
      throw new IOException(e);
    }
  }

  private ImageData createImageLoader(final String name) {
    if (name.endsWith(".tga")) {
      return new TGAImageData();
    }
    return new ImageIOImageData();
  }

  private void addNewBatch() {
    currentBatch.end();

    currentBatch = batchPool.allocate();
    batches.add(currentBatch);
    currentBatch.begin();
  }

  private void checkGLError() {
    int error= GL11.glGetError();
    if (error != GL11.GL_NO_ERROR) {
      String glerrmsg = GLU.gluErrorString(error);
      log.warning("Error: (" + error + ") " + glerrmsg);
      try {
        throw new Exception();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private void addQuad(
      final float x,
      final float y,
      final float width,
      final float height,
      final Color color1,
      final Color color2,
      final Color color3,
      final Color color4,
      final int textureX,
      final int textureY,
      final int textureWidth,
      final int textureHeight) {
    if (!currentBatch.canAddQuad()) {
      addNewBatch();
    }
    if (isOutsideClippingRectangle(x, y, width, height)) {
      completeClippedCounter++;
      return;
    }
    currentBatch.addQuad(
        x,
        y,
        width,
        height,
        color1,
        color2,
        color3,
        color4,
        textureX,
        textureY,
        textureWidth,
        textureHeight,
        currentClippingX0,
        currentClippingY0,
        currentClippingX1,
        currentClippingY1);
  }

  private boolean isOutsideClippingRectangle(final float x, final float y, final float width, final float height) {
    if (x > currentClippingX1) {
      return true;
    }
    if ((x + width) < currentClippingX0) {
      return true;
    }
    if (y > currentClippingY1) {
      return true;
    }
    if ((y + height) < currentClippingY0) {
      return true;
    }
    return false;
  }

  private interface BatchMode {
    FloatBuffer begin(final CoreVBO vbo);
    void end(final CoreVBO vbo);
  }

  private class BatchModeMapBuffer implements BatchMode {

    @Override
    public FloatBuffer begin(final CoreVBO vbo) {
      return vbo.getMappedBuffer();
    }

    @Override
    public void end(final CoreVBO vbo) {
      vbo.unmapBuffer();
    }
  }

  private class BatchModeBufferData implements BatchMode {

    @Override
    public FloatBuffer begin(final CoreVBO vbo) {
      vbo.getBuffer().rewind();
      return vbo.getBuffer();
    }

    @Override
    public void end(final CoreVBO vbo) {
      vbo.getBuffer().rewind();
      vbo.send();
    }
  }

  private class Batch {
    private final int SIZE = 64*1024;

    private int primitiveCount;
    private final CoreVAO vao;
    private final CoreVBO vbo;
    private final CoreElementVBO elementVbo;
    private FloatBuffer vertexBuffer;
    private final BatchMode batchMode;
    private int globalIndex;
    private int indexCount;
    private IntBuffer indexBuffer;

    private Batch(final BatchMode algorithm) {
      batchMode = algorithm;
      vao = new CoreVAO();
      vao.bind();

      elementVbo = CoreElementVBO.createStream(new int[SIZE/4]);
      indexBuffer = elementVbo.getBuffer();

      vbo = CoreVBO.createStream(new float[SIZE]);
      vbo.bind();

      vao.enableVertexAttributef(niftyShader.getAttribLocation("aVertex"), 2, 8, 0);
      vao.enableVertexAttributef(niftyShader.getAttribLocation("aColor"), 4, 8, 2);
      vao.enableVertexAttributef(niftyShader.getAttribLocation("aTexture"), 2, 8, 6);

      primitiveCount = 0;
      globalIndex = 0;
      indexCount = 0;
      vao.unbind();
    }

    public int getIndexCount() {
      return indexCount;
    }

    public void bind() {
      vao.bind();
    }

    public int getPrimitiveCount() {
      return primitiveCount;
    }

    public void begin() {
      vao.bind();
      vbo.bind();
      vertexBuffer = batchMode.begin(vbo);
      indexBuffer.rewind();
      primitiveCount = 0;
      globalIndex = 0;
      indexCount = 0;
      vao.unbind();
    }

    public void end() {
      vbo.bind();
      batchMode.end(vbo);

      elementVbo.bind();
      indexBuffer.rewind();
      elementVbo.send();
    }

    public boolean canAddQuad() {
      return ((primitiveCount + 1) * PRIMITIVE_SIZE) < SIZE;
    }

    private void addQuad(
        final float x,
        final float y,
        final float width,
        final float height,
        final Color color1,
        final Color color2,
        final Color color3,
        final Color color4,
        final int textureX,
        final int textureY,
        final int textureWidth,
        final int textureHeight,
        final float clipX0,
        final float clipY0,
        final float clipX1,
        final float clipY1) {
      int bufferIndex = 0;
      int elementIndexBufferIndex = 0;

      primitiveBuffer[bufferIndex++] = x;
      primitiveBuffer[bufferIndex++] = y + height;
      primitiveBuffer[bufferIndex++] = color3.getRed();
      primitiveBuffer[bufferIndex++] = color3.getGreen();
      primitiveBuffer[bufferIndex++] = color3.getBlue();
      primitiveBuffer[bufferIndex++] = color3.getAlpha();
      primitiveBuffer[bufferIndex++] = calcU(textureX, generator.getWidth());
      primitiveBuffer[bufferIndex++] = calcU(textureY + textureHeight - 1, generator.getHeight());
      elementIndexBuffer[elementIndexBufferIndex++] = globalIndex++;

      primitiveBuffer[bufferIndex++] = x + width;
      primitiveBuffer[bufferIndex++] = y + height;
      primitiveBuffer[bufferIndex++] = color4.getRed();
      primitiveBuffer[bufferIndex++] = color4.getGreen();
      primitiveBuffer[bufferIndex++] = color4.getBlue();
      primitiveBuffer[bufferIndex++] = color4.getAlpha();
      primitiveBuffer[bufferIndex++] = calcU(textureX + textureWidth - 1, generator.getWidth());
      primitiveBuffer[bufferIndex++] = calcU(textureY + textureHeight - 1, generator.getHeight());
      elementIndexBuffer[elementIndexBufferIndex++] = globalIndex++;

      primitiveBuffer[bufferIndex++] = x;
      primitiveBuffer[bufferIndex++] = y;
      primitiveBuffer[bufferIndex++] = color1.getRed();
      primitiveBuffer[bufferIndex++] = color1.getGreen();
      primitiveBuffer[bufferIndex++] = color1.getBlue();
      primitiveBuffer[bufferIndex++] = color1.getAlpha();
      primitiveBuffer[bufferIndex++] = calcU(textureX, generator.getWidth());
      primitiveBuffer[bufferIndex++] = calcU(textureY, generator.getHeight());
      elementIndexBuffer[elementIndexBufferIndex++] = globalIndex++;

      primitiveBuffer[bufferIndex++] = x + width;
      primitiveBuffer[bufferIndex++] = y;
      primitiveBuffer[bufferIndex++] = color2.getRed();
      primitiveBuffer[bufferIndex++] = color2.getGreen();
      primitiveBuffer[bufferIndex++] = color2.getBlue();
      primitiveBuffer[bufferIndex++] = color2.getAlpha();
      primitiveBuffer[bufferIndex++] = calcU(textureX + textureWidth - 1, generator.getWidth());
      primitiveBuffer[bufferIndex++] = calcU(textureY, generator.getHeight());
      elementIndexBuffer[elementIndexBufferIndex++] = globalIndex++;
      elementIndexBuffer[elementIndexBufferIndex++] = PRIMITIVE_RESTART_INDEX;

      indexCount += 5;

      vertexBuffer.put(primitiveBuffer);
      indexBuffer.put(elementIndexBuffer);
      primitiveCount++;
    }

    private float calcU(final int value, final int max) {
      return (0.5f / (float) max) + (value / (float) max);
    }
  }

  private class FontRenderer implements BitmapFontRenderer {
    private final SimpleImageLoader loader = new SimpleImageLoader();
    private final CoreTextureAtlasGenerator atlas;
    private final Map<String, BitmapInfo> textureInfos = new HashMap<String, BitmapInfo>();

    public FontRenderer(final CoreTextureAtlasGenerator atlas) {
      this.atlas = atlas;
    }

    @Override
    public void registerBitmap(final String bitmapId, final InputStream data, final String filename) throws IOException {
      de.lessvoid.simpleimageloader.ImageData imageData = loader.load(filename, data, new SimpleImageLoaderConfig().forceAlpha());
      Result result = atlas.addImage(createTexture(imageData), filename, 0);
      if (result == null) {
        throw new BitmapFontException("failed to add image to texture atlas: " + filename);
      }
      textureInfos.put(bitmapId, new BitmapInfo(result));
    }

    @Override
    public void registerGlyph(
        final String bitmapId,
        final char c,
        final int xoff,
        final int yoff,
        final int w,
        final int h,
        final float u0,
        final float v0,
        final float u1,
        final float v1) {
      BitmapInfo textureInfo = textureInfos.get(bitmapId);
      int atlasX0 = textureInfo.getX();
      int atlasY0 = textureInfo.getY();
      int atlasImageW = textureInfo.getOriginalImageWidth();
      int atlasImageH = textureInfo.getOriginalImageHeight();
      int u = (int) (atlasX0 + u0 * atlasImageW);
      int v = (int) (atlasY0 + v0 * atlasImageH);

      textureInfo.addCharRenderInfo(c, new CharRenderInfo(xoff, yoff, w, h, u, v));
    }

    @Override
    public void prepare() {
    }

    @Override
    public void beforeRender() {
    }

    @Override
    public void render(
        final String bitmapId,
        final int x,
        final int y,
        final char c,
        final float sx,
        final float sy,
        final float r,
        final float g,
        final float b,
        final float a) {
      textColor.setRed(r);
      textColor.setGreen(g);
      textColor.setBlue(b);
      textColor.setAlpha(a);
      textureInfos.get(bitmapId).renderCharacter(c, x, y, sx, sy, textColor);
      glyphCount++;
    }

    @Override
    public void afterRender() {
    }

    private CoreTexture2D createTexture(final de.lessvoid.simpleimageloader.ImageData imageData) {
      return new CoreTexture2D(ColorFormat.RGBA, imageData.getWidth(), imageData.getHeight(), imageData.getData(), ResizeFilter.Linear);
    }
  }

  private class CharRenderInfo {
    final int xoff;
    final int yoff;
    final int w;
    final int h;
    final int u0;
    final int v0;

    public CharRenderInfo(
        final int xoff,
        final int yoff,
        final int w,
        final int h,
        final int u0,
        final int v0) {
      this.xoff = xoff;
      this.yoff = yoff;
      this.w = w;
      this.h = h;
      this.u0 = u0;
      this.v0 = v0;
    }

    public void renderQuad(final int x, final int y, final float sx, final float sy, final Color textColor) {
      addQuad(
          x + (float) Math.floor(xoff * sx),
          y + (float) Math.floor(yoff * sy),
          (w * sx),
          (h * sy),
          textColor,
          textColor,
          textColor,
          textColor,
          u0,
          v0,
          w,
          h);
    }
  }

  private static class BitmapInfo {
    private final Result result;
    private final Map<Character, CharRenderInfo> characterIndices = new Hashtable<Character, CharRenderInfo>();

    public BitmapInfo(final Result result) {
      this.result = result;
    }

    public void renderCharacter(char c, int x, int y, float sx, float sy, Color textColor) {
      characterIndices.get(c).renderQuad(x, y, sx, sy, textColor);
    }

    public int getX() {
      return result.getX();
    }

    public int getY() {
      return result.getY();
    }

    public int getOriginalImageWidth() {
      return result.getOriginalImageWidth();
    }

    public int getOriginalImageHeight() {
      return result.getOriginalImageHeight();
    }

    public void addCharRenderInfo(final Character c, final CharRenderInfo renderInfo) {
      this.characterIndices.put(c, renderInfo);
    }
  }

}
