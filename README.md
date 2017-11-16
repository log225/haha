usage:
1. 编译好的apk 安装一下
   运行下面的脚本
```
adb forward tcp:56789 tcp:56789
adb shell "export CLASSPATH=/data/app/cn.iotguard.phonecontroller-1/base.apk;exec app_process /system/bin cn.iotguard.phonecontroller.Main"
```
做的事情很简单，如果手机上没有安装该apk则安装，然后进行了端口映射，最后启动服务程序。

2. 打开`viewer.html`