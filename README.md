# LiveVideo
 Stream live video between android devices over Wi-Fi through TCP with low latency.
 
一

## 简介

最近，项目中需要实现这样一个需求，局域网内两台设备，一台通过摄像头录像，并实时传输到另一台设备上显示，要求延迟在1秒之内。  
调研了一下局域网内视频传输的东西，网上找了一些相关项目，找到的最接近需求的是[Endoscope](https://github.com/hypeapps/Endoscope)，只不过这个实现会有4-5秒的延迟，造成延迟的主要原因在于他是在录制端开启了一个rtsp服务：  
> StartStreamPresenter.java

```
  private void startRtspServer() {
        rtspServer = new RtspServer();
        rtspServer.addCallbackListener(this);
        rtspServer.start();
    }
```

显示端使用Android原生MediaPlayer去链接视屏源并播放：  
> PlayerStreamActivity.java  

```
mediaPlayer.setDataSource(this, videoUri);
```

通过调试发现视屏发送端在有客户端链接后，立即开始录制并输出视屏数据，怀疑是MediaPlayer的缓存机制导致了视频不同步的问题。

因此，在此基础上：

* 我将视频传输协议改为tcp，发送的数据事先做了转换NV21->YUV420,并矫正了90度旋转（为了竖屏正常显示，摄像头需要设置为顺时针旋转90度)
* 接收端改为通过硬件解码将视频数据包解析并渲染到SurfaceView上


\##########################################  

另，在网上看到很多实现方式是通过将每帧数据压缩为Jpeg格式之后传输、显示，这里我也将这种方式实现了一下，通过对比发现效果很不理想，可以通过修改`DetectConst.TRANS_MODE`来切换测试
