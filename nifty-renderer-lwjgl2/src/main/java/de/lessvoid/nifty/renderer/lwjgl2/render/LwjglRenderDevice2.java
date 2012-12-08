package de.lessvoid.nifty.renderer.lwjgl2.render;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Cursor;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Matrix4f;

import de.lessvoid.coregl.CoreMatrixFactory;
import de.lessvoid.coregl.CoreRender;
import de.lessvoid.coregl.CoreShader;
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

/**
 * Lwjgl RenderDevice Implementation.
 * @author void
 */
public class LwjglRenderDevice2 implements RenderDevice {
  private static final int PRIMITIVE_SIZE = 6*12; // 6 vertices per quad and 12 vertex attributes per vertex (2xpos, 2xtexture, 4xcolor, 4xclipping)
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

  /**
   * The standard constructor. You'll use this in production code. Using this
   * constructor will configure the RenderDevice to not log FPS on System.out.
   */
  public LwjglRenderDevice2() {
    time = System.currentTimeMillis();
    frames = 0;

    modelViewProjection = CoreMatrixFactory.createOrtho(0, getWidth(), getHeight(), 0);

    niftyShader = CoreShader.newShaderWithVertexAttributes("aVertex", "aColor", "aTexture", "aClipping");
    niftyShader.fragmentShader("nifty.fs");
    niftyShader.vertexShader("nifty.vs");
    niftyShader.link();
    niftyShader.activate();
    niftyShader.setUniformMatrix4f("uModelViewProjectionMatrix", modelViewProjection);
    niftyShader.setUniformi("uTex", 0);

    batchPool = new ObjectPool<Batch>(10, new Factory<Batch>() {
      @Override
      public Batch createNew() {
        return new Batch();
      }
    });

    generator = new CoreTextureAtlasGenerator(2048, 2048);
    plainImage = (LwjglRenderImage2) createImage("nifty.tga", false);

    niftyShader.activate();
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
    if (log.isLoggable(Level.INFO)) {
      log.info("Viewport: " + viewportWidth + ", " + viewportHeight);
    }
  }

  public void beginFrame() {
    log.fine("beginFrame()");

    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    setBlendMode(BlendMode.BLEND);

    //GL11.glDisable(GL11.GL_SCISSOR_TEST);
    currentClipping = false;
    currentClippingX0 = 0;
    currentClippingY0 = 0;
    currentClippingX1 = getWidth();
    currentClippingY1 = getHeight();

    for (int i=0; i<batches.size(); i++) {
      batchPool.free(batches.get(i));
      batches.get(i).reset();
    }
    batches.clear();
    batches.add(batchPool.allocate());
    currentBatch = batches.get(0);
    currentBatch.reset();

    generator.getDone().bindTexture();
    glyphCount = 0;
  }

  public void endFrame() {
    log.fine("endFrame");

    niftyShader.activate();

    int totalPrimitiveCount = 0;
    for (int i=0; i<batches.size(); i++) {
      Batch batch = batches.get(i);

      int primitiveCount = batch.send();
      CoreRender.renderTriangles(primitiveCount*6);

      totalPrimitiveCount += primitiveCount;
    }

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
      buffer.append(totalPrimitiveCount*6);
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
    if (displayFPS) {
     // renderFont(fpsFont, buffer.toString(), 10, getHeight() - fpsFont.getHeight() - 10, Color.WHITE, 1.0f, 1.0f);
    }

    // currently the RenderDevice interface does not support a way to be notified when the resolution is changed
    // so we reset the viewportWidth and viewportHeight here so that we only call getViewport() once per frame and
    // not each time someone calls getWidth() or getHeight().
    viewportWidth = -1;
    viewportHeight = -1;

    checkGLError();
  }

  public void clear() {
    log.fine("clear()");

    GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
  }

  /**
   * Create a new RenderImage.
   * @param filename filename
   * @param filterLinear linear filter the image
   * @return RenderImage
   */
  public RenderImage createImage(final String filename, final boolean filterLinear) {
    return new LwjglRenderImage2(generator, filename, filterLinear, resourceLoader2);
  }

  /**
   * Create a new RenderFont.
   * @param filename filename
   * @return RenderFont
   */
  public RenderFont createFont(final String filename) {
    return new LwjglRenderFont2(filename, this, resourceLoader);
  }

  /**
   * Render a quad.
   * @param x x
   * @param y y
   * @param width width
   * @param height height
   * @param color color
   */
  public void renderQuad(final int x, final int y, final int width, final int height, final Color color) {
    log.fine("renderQuad()");
    addQuad(x, y, width, height, color, color, color, color, plainImage.getX(), plainImage.getY(), plainImage.getWidth(), plainImage.getHeight());
  }

  public void renderQuad(final int x, final int y, final int width, final int height, final Color topLeft, final Color topRight, final Color bottomRight, final Color bottomLeft) {
    log.fine("renderQuad2()");
    addQuad(x, y, width, height, topLeft, topRight, bottomLeft, bottomRight, plainImage.getX(), plainImage.getY(), plainImage.getWidth(), plainImage.getHeight());
  }

  /**
   * Render the image using the given Box to specify the render attributes.
   * @param x x
   * @param y y
   * @param width width
   * @param height height
   * @param color color
   * @param scale scale
   */
  public void renderImage(final RenderImage image, final int x, final int y, final int width, final int height, final Color c, final float scale) {
    log.fine("renderImage()");

    if (width < 0) {
      log.warning("Attempt to render image with negative width");
      return;
    }
    if (height < 0) {
      log.warning("Attempt to render image with negative height");
      return;
    }

    LwjglRenderImage2 img = (LwjglRenderImage2) image;
    addQuad(x, y, width, height, c, c, c, c, img.getX(), img.getY(), img.getWidth(), img.getHeight());
  }

  /**
   * Render sub image.
   * @param x x
   * @param y y
   * @param w w
   * @param h h
   * @param srcX x
   * @param srcY y
   * @param srcW w
   * @param srcH h
   * @param color color
   */
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
    log.fine("renderImage2()");

    if (w < 0) {
      log.warning("Attempt to render image with negative width");
      return;
    }
    if (h < 0) {
      log.warning("Attempt to render image with negative height");
      return;
    }

    LwjglRenderImage2 img = (LwjglRenderImage2) image;
    addQuad(x, y, w, h, c, c, c, c, img.getX() + srcX, img.getY() + srcY, srcW, srcH);
  }

  /**
   * render the text.
   * @param text text
   * @param x x
   * @param y y
   * @param color color
   * @param fontSize size
   */
  public void renderFont(final RenderFont font, final String text, final int x, final int y, final Color color, final float fontSizeX, final float fontSizeY) {
    log.fine("renderFont()");
/*
    if (!currentTexturing) {
      GL11.glEnable(GL11.GL_TEXTURE_2D);
      currentTexturing = true;
    }
    setBlendMode(BlendMode.BLEND);
    if (color == null) {
      glyphCount += ((LwjglRenderFont)font).getFont().drawStringWithSize(x, y, text, fontSizeX, fontSizeY);
    } else {
      glyphCount += ((LwjglRenderFont)font).getFont().renderWithSizeAndColor(x, y, text, fontSizeX, fontSizeY, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }
*/
  }

  /**
   * Enable clipping to the given region.
   * @param x0 x0
   * @param y0 y0
   * @param x1 x1
   * @param y1 y1
   */
  public void enableClip(final int x0, final int y0, final int x1, final int y1) {
    log.fine("enableClip()");

    if (currentClipping && currentClippingX0 == x0 && currentClippingY0 == y0 && currentClippingX1 == x1 && currentClippingY1 == y1) {
      return;
    }
    currentClipping = true;
    currentClippingX0 = x0;
    currentClippingY0 = y0;
    currentClippingX1 = x1;
    currentClippingY1 = y1;
    //GL11.glScissor(x0, getHeight() - y1, x1 - x0, y1 - y0);
    //GL11.glEnable(GL11.GL_SCISSOR_TEST);
    //addNewBatch("scissor");
  }

  /**
   * Disable Clip.
   */
  public void disableClip() {
    log.fine("disableClip()");

    if (!currentClipping) {
      return;
    }
    //GL11.glDisable(GL11.GL_SCISSOR_TEST);
    currentClipping = false;
    currentClippingX0 = 0;
    currentClippingY0 = 0;
    currentClippingX1 = getWidth();
    currentClippingY1 = getHeight();
    //addNewBatch("disableClip");
  }

  public void setBlendMode(final BlendMode renderMode) {
    log.fine("setBlendMode()");

    if (renderMode.equals(currentBlendMode)) {
      return;
    }

    currentBlendMode = renderMode;
    if (currentBlendMode.equals(BlendMode.BLEND)) {
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    } else if (currentBlendMode.equals(BlendMode.MULIPLY)) {
      GL11.glBlendFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO);
    }
    addNewBatch("blendMode");
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

  private void addNewBatch(final String reason) {
    //System.out.println(reason);
    currentBatch = batchPool.allocate();
    batches.add(currentBatch);
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
      final int x,
      final int y,
      final int width,
      final int height,
      final Color color1,
      final Color color2,
      final Color color3,
      final Color color4,
      final int textureX,
      final int textureY,
      final int textureWidth,
      final int textureHeight) {
    if (!currentBatch.canAddQuad()) {
      addNewBatch("size");
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

  private class Batch {
    private int primitiveCount;
    private final CoreVAO vao;
    private final CoreVBO vbo;
    private final FloatBuffer vertexBuffer;
    private final int SIZE = 10000;

    private Batch() {
      vao = new CoreVAO();
      vao.bind();

      vbo = CoreVBO.createStream(new float[SIZE]);
      vbo.bind();

      vao.enableVertexAttributef(niftyShader.getAttribLocation("aVertex"), 2, 12, 0);
      vao.enableVertexAttributef(niftyShader.getAttribLocation("aColor"), 4, 12, 2);
      vao.enableVertexAttributef(niftyShader.getAttribLocation("aTexture"), 2, 12, 6);
      vao.enableVertexAttributef(niftyShader.getAttribLocation("aClipping"), 4, 12, 8);

      vertexBuffer = vbo.getBuffer();
      primitiveCount = 0;
    }

    public boolean canAddQuad() {
      return ((primitiveCount + 1) * PRIMITIVE_SIZE) < SIZE;
    }

    public void reset() {
      vertexBuffer.rewind();
      primitiveCount = 0;
    }

    public int send() {
      vertexBuffer.rewind();
      vao.bind();
      vbo.send();
      return primitiveCount;
    }

    private void addQuad(
        final int x,
        final int y,
        final int width,
        final int height,
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
      float[] buffer = new float[PRIMITIVE_SIZE];
      int index = 0;
      buffer[index++] = x;
      buffer[index++] = y + height;
      buffer[index++] = color3.getRed();
      buffer[index++] = color3.getGreen();
      buffer[index++] = color3.getBlue();
      buffer[index++] = color3.getAlpha();
      buffer[index++] = textureX / (float) generator.getWidth();
      buffer[index++] = (textureY + textureHeight) / (float) generator.getHeight();
      buffer[index++] = clipX0; // x
      buffer[index++] = clipY0; // y
      buffer[index++] = clipX1; // z
      buffer[index++] = clipY1; // w

      buffer[index++] = x;
      buffer[index++] = y;
      buffer[index++] = color1.getRed();
      buffer[index++] = color1.getGreen();
      buffer[index++] = color1.getBlue();
      buffer[index++] = color1.getAlpha();
      buffer[index++] = textureX / (float) generator.getWidth();
      buffer[index++] = textureY / (float) generator.getHeight();
      buffer[index++] = clipX0;
      buffer[index++] = clipY0;
      buffer[index++] = clipX1;
      buffer[index++] = clipY1;

      buffer[index++] = x + width;
      buffer[index++] = y + height;
      buffer[index++] = color4.getRed();
      buffer[index++] = color4.getGreen();
      buffer[index++] = color4.getBlue();
      buffer[index++] = color4.getAlpha();
      buffer[index++] = (textureX + textureWidth) / (float) generator.getWidth();
      buffer[index++] = (textureY + textureHeight) / (float) generator.getHeight();
      buffer[index++] = clipX0;
      buffer[index++] = clipY0;
      buffer[index++] = clipX1;
      buffer[index++] = clipY1;

      buffer[index++] = x;
      buffer[index++] = y;
      buffer[index++] = color1.getRed();
      buffer[index++] = color1.getGreen();
      buffer[index++] = color1.getBlue();
      buffer[index++] = color1.getAlpha();
      buffer[index++] = textureX / (float) generator.getWidth();
      buffer[index++] = textureY / (float) generator.getHeight();
      buffer[index++] = clipX0;
      buffer[index++] = clipY0;
      buffer[index++] = clipX1;
      buffer[index++] = clipY1;

      buffer[index++] = x + width;
      buffer[index++] = y + height;
      buffer[index++] = color4.getRed();
      buffer[index++] = color4.getGreen();
      buffer[index++] = color4.getBlue();
      buffer[index++] = color4.getAlpha();
      buffer[index++] = (textureX + textureWidth) / (float) generator.getWidth();
      buffer[index++] = (textureY + textureHeight) / (float) generator.getHeight();
      buffer[index++] = clipX0;
      buffer[index++] = clipY0;
      buffer[index++] = clipX1;
      buffer[index++] = clipY1;

      buffer[index++] = x + width;
      buffer[index++] = y;
      buffer[index++] = color2.getRed();
      buffer[index++] = color2.getGreen();
      buffer[index++] = color2.getBlue();
      buffer[index++] = color2.getAlpha();
      buffer[index++] = (textureX + textureWidth) / (float) generator.getWidth();
      buffer[index++] = textureY / (float) generator.getHeight();
      buffer[index++] = clipX0;
      buffer[index++] = clipY0;
      buffer[index++] = clipX1;
      buffer[index++] = clipY1;

      vertexBuffer.put(buffer);
      primitiveCount++;
    }
  }
}
