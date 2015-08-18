# ZFM20Module
Java class for interfacing ZFM20 fingerprint module
Methods are name according to the user manual on Adafruit page https://github.com/adafruit/Adafruit-Fingerprint-Sensor-Library/tree/master/documentation  
According to manual, after making connection, the module cannot accept command for at least 500ms. I found that to be really sure, you need at least 1.5-2 s. Recommed that you change baudrate to get better throughput. To do this  
```
ZFM20Module module = new ZFM20Module();  
Thread.sleep(1500);
boolean success =module.setBaud(12); //actual baudrate = 12*9600 (max)
module.close();
```  
Then change value of `defaultBaudrate`.  
Note that every command will resend command if timeout or failure. Change # of max retry by changing value of `ATTEMPT` in file. Also module.genImg() is blocking  
<b>Example of use</b>  
<b>(1)Getting raw image</b>  
```
ZFM20Module module = new ZFM20Module();
Thread.sleep(1500);
while(true)
{
  while (true) {
      module.genImg();
      if (!module.img2tz(1)) System.out.println(module.errmsg);
      else break;
  }
  while (true) {
      module.genImg();
      if (!module.img2tz(2)) System.out.println(module.errmsg);
      else break;
  }
  if (module.regmodel()) break;
}
boolean success = module.upImg();
module.close();
//if success then the image will be in module.fingerRaw (already in 8 bit greyscale. no header)
```    
<b>(2)Enroll (incremental ID)</b>  
```
ZFM20Module module = new ZFM20Module();
Thread.sleep(1500);
while(true)
{
  while (true) {
      module.genImg();
      if (!module.img2tz(1)) System.out.println(module.errmsg);
      else break;
  }
  while (true) {
      module.genImg();
      if (!module.img2tz(2)) System.out.println(module.errmsg);
      else break;
  }
  if (module.regmodel()) break;
}
int cur = 0;
  if (module.getTemplateCount()) 
  {
      cur = module.templatecount;
  } else System.exit(-1);

  if (module.store(1, cur)) {
      System.out.println("Storing successful for template#" + cur);
  }
module.close();
```
<b>(3)Search</b>
```
ZFM20Module module = new ZFM20Module();
Thread.sleep(1500);
int cur = 0;
while (true) {
      module.genImg();
      if (!module.img2tz(1)) System.out.println(module.errmsg);
      else break;
}
if (module.getTemplateCount()) 
{
    cur = module.templatecount;
} else System.exit(-1);
boolean sucess = module.search(1,cur);
module.close();
```
