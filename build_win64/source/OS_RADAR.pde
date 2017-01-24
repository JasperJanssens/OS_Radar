//////////////////////////////////
//////     OS_RADAR v2.0    //////
//////////////////////////////////

/*
Date 2017/01/19

Created by Jasper Janssens for OLYMPIA STADION
a video installation by David Claerbout

This is a Processing v3.2.3 script written in Java
using the controlP5 library for the user interface
It is used in conjunction with a browser & Screenhunter Pro 6.0

AT this time i configured firefox with extension 'Tab Reloader' that reloads this page every 5 minutes:
https://weather.com/weather/radar/interactive/l/GMXX8440:1:GM

Screenhunter then runs in the background making a screenshot of the PC screen every minute and saves this to the OS_RADAR script folder (eg. C:\OS_RADAR\)
*/

// import controlP5 library for UI
import controlP5.*;

// some general variables
PImage webImg;
XML xmlWeatherData;
public int timer;

// variables for loading, using & saving config.xml
XML xmlConfig;
int positionX, positionY;
String xmlUrl;
String xmlSave;

// variables for analyzing the sampled pixel
float sampleH;
float sampleS;
float sampleB;

// variables for modifying the weather data
boolean isImgValid;
int weatherMode;
float weatherValue;
float weatherClouds;

// variables for creation of UI controls
ControlP5 cp5;
Toggle t1, t2;
Numberbox n1;
Slider s1;

// variables for weather override, assigned to UI controls
boolean on_off = false;
boolean rain_snow = false;
float sliderValue = 50;
int timerOverride;;

//////////////////////////////////
//////    SETUP FUNCTION    //////
//////////////////////////////////
// runs when you start the program

void setup ()  
{
  // set timer to 5 minute countdown
  timer = 5;
  
  // initialize variable
  isImgValid = true;
  
  // setup canvas width & height
  size (400,400);
  
  //////////////////////////////////
  //////   ADD UI CONTROLS    //////
  //////////////////////////////////
  // using ControlP5 library
 
 // initialize UI class
  cp5 = new ControlP5(this);  
  
  // add toggle for ON/OFF
  t1 = cp5.addToggle("on_off")
          .setPosition (10, 15)
          .setSize (40, 20)
          .setValue(false);
     
  // add toggle for rain or snow
  t2 = cp5.addToggle("rain_snow")
          .setPosition (80, 15)
          .setSize (40, 20)
          .setValue(true)
          .setMode(ControlP5.SWITCH);
     
  // add numberbox for setting the timer
  n1 = cp5.addNumberbox("timerOverride")
          .setPosition(160,15)
          .setSize(80,20)
          .setRange(0,300)
          .setLabel ("Timer")
          .setValue(timer);
  
  // add slider for amount of precipitation
  s1 = cp5.addSlider ("sliderValue")
          .setPosition (10, 60)
          .setSize (100, 20)
          .setLabel ("Amount")
          .setRange (30,100);
          
  //////////////////////////////////
  //////   ADD UI CALLBACKS   //////
  //////////////////////////////////
  // using ControlP5 library
  // these functions allow interaction with the UI during runtime and give the UI controls their function
  
  // If toggle ON/OFF is activated, overwrite XML with set variables & set timer to timer override variable
  // if toggle ON/OFF is deactivated, reset timer to zero & let the normal process resume 
  t1.addCallback(new CallbackListener() 
  {
    public void controlEvent(CallbackEvent theEvent) 
    {
      if (theEvent.getAction()==ControlP5.ACTION_RELEASE && on_off == true) 
      {
        weatherOverride ();
        modifyWeatherData (true, weatherMode, weatherValue, weatherClouds);
        timer = timerOverride;
      }
      
      if (theEvent.getAction()==ControlP5.ACTION_RELEASE && on_off == false) 
      {
        timer = 0;
      }
    }
  });
  
  // if toggle RAIN/SNOW is changed and toggle ON/OFF is activated, overwrite XML with set variables
  t2.addCallback(new CallbackListener() 
  {
    public void controlEvent(CallbackEvent theEvent) 
    {
      if (theEvent.getAction()==ControlP5.ACTION_RELEASE && on_off == true) 
      {
        weatherOverride ();
        modifyWeatherData (true, weatherMode, weatherValue, weatherClouds);
      }
    }
  });
  
  // if slider VALUE is changed and toggle ON/OFF is activated, overwrite XML with set variables 
  s1.addCallback(new CallbackListener() 
  {
    public void controlEvent(CallbackEvent theEvent) 
    {
      if ((theEvent.getAction()==ControlP5.ACTION_RELEASE || theEvent.getAction()==ControlP5.ACTION_RELEASE_OUTSIDE) && on_off == true) 
      {
        weatherOverride ();
        modifyWeatherData (true, weatherMode, weatherValue, weatherClouds);
      }
    }
  });
  
  // if numberbox TIMER OVERRIDE is changed and toggle ON/OFF is activated, set timer to new value
  n1.addCallback(new CallbackListener() 
  {
    public void controlEvent(CallbackEvent theEvent) 
    {
      if ((theEvent.getAction()==ControlP5.ACTION_RELEASE || theEvent.getAction()==ControlP5.ACTION_RELEASE_OUTSIDE) && on_off == true) 
      {
        timer = timerOverride;
      }
    }
  });
  
  ///////////////////////////////////////
  //////   RUN SCRIPT FIRST TIME   //////
  ///////////////////////////////////////
  
  loadConfig ();
  runScript ();
}

/////////////////////////////
//////   LOAD CONFIG   //////
/////////////////////////////

void loadConfig ()
{
  xmlConfig = loadXML ("config.xml");
  
  positionX = xmlConfig.getChild("imgPosition").getInt("x");
  positionY = xmlConfig.getChild("imgPosition").getInt("y");
  xmlUrl = xmlConfig.getChild("xmlWeatherData").getString("url");
  xmlSave = xmlConfig.getChild("xmlSaveLocation").getString("path"); // C:/Users/Administrator/Dropbox/Weatherdata/
}

/////////////////////////////////////
//////   RUN SCRIPT FUNCTION   //////
/////////////////////////////////////

void runScript ()
{
  sampleImage ();

  if (webImg != null)
  {
    analyzePixel ();
    modifyWeatherData (isImgValid, weatherMode, weatherValue, weatherClouds);
  }
  else
  {
    modifyWeatherData (true, 0, 0, 0);
  }
}

////////////////////////////////////////////////
//////    UI WEATHER OVERRIDE FUNCTION    //////
////////////////////////////////////////////////
// converts the variables set in the UI to variables used to modify the weather data XML

void weatherOverride ()
{
    if (rain_snow ==  true)
    { 
      weatherMode = 1;
    }  
    else
    {
      weatherMode = 2;
    }
    
    weatherValue = sliderValue;
    weatherClouds = map (sliderValue, 30, 100, 60, 100);
}

///////////////////////////////
//////   DRAW FUNCTION   //////
///////////////////////////////
// updates every frame

void draw ()
{
  // run TIMER FUNCTION
  setTimer ();
  
  // when timer runs out & weather override is OFF, reset timer & RUN SCRIPT FUNCTION
  if (timer <= 0 && on_off == false)
  {
    timer = 5;
    n1.setValue (timer);
    runScript ();
  }
}

//////////////////////////////////
//////    TIMER FUNCTION    //////
//////////////////////////////////

void setTimer ()
{
  // when the computer clock starts a new minute, decrease the timer value by 1
  if (second () == 0)
  {
    delay (2000);
    timer --;
    n1.setValue (timer);
    
    // if the weather override is ON, set it to OFF when the timer runs out
    if (timer <= 0 && on_off == true)
    {
      t1.setValue (false);
    }
  }
}

//////////////////////////////////////////////////////
//////    SAMPLE DESKTOP SCREENSHOT FUNCTION    //////
//////////////////////////////////////////////////////
// loads the captured screenshot and samples the hue, saturation & brightness
// saturation & brightness are sampled in 8bit, so 100% is equal to a value of 255 (percent = 8bitvalue/255*100)
// the hue is also sampled in 8bit, but it is translated from a color wheel value (360°) (angle = 8bitvalue/255*360)

void sampleImage ()
{
  webImg = loadImage ("OS_RADAR.jpg");
    
  if (webImg != null)
  {
    image (webImg, -(positionX - 200), -(positionY - 220));
    color samplePixel = get (200, 220);

    fill (50);
    rect (0, 0, width, 100);
    
    fill (samplePixel);
    rect (320, 15, 65, 70);
    
    sampleH = hue (samplePixel);
    sampleS = saturation (samplePixel);
    sampleB = brightness (samplePixel);
    
    stroke (100);
    strokeWeight (3);
    line (150, 220, 250, 220);
    line (200, 170, 200, 270);
    noFill ();
    ellipse (200, 220, 50, 50);
  }
}

//////////////////////////////////////////
//////    ANALYZE PIXEL FUNCTION    //////
//////////////////////////////////////////
// analyzes the different components of the analyzed pixel and translates them to variables used to modify the weather data XML

void analyzePixel ()
{
  // black screenshot, so set XML date way back
  if (sampleB < 25)
  {
    isImgValid = false;
    return;
  }
  
  // no precipitation
  if (sampleS < 50 && sampleB > 230)
  {
     weatherMode = 0;
     weatherValue = 0;
     return;
  }
   
  // rain
  if (sampleH <= 106 || sampleH >= 245)
  {
     weatherMode = 1;
      
     if (sampleH >= 245)
     {
       sampleH = 0;
     }
     
     if (sampleH >= 80)
     {
       sampleB = constrain (sampleB, 114, 205);
       weatherValue = map (sampleB, 114, 205, 50, 30);
       weatherClouds = map (sampleB, 114, 205, 80, 60);
       return;
     }
     
     if (sampleH < 80)
     {
       sampleH = constrain (sampleH, 0, 60);
       weatherValue = map (sampleH, 0, 60, 100, 51);
       weatherClouds = map (sampleH, 0, 60, 100, 81);
       return;
     }
  }
    
  // snow
  if (sampleH >= 120 || sampleH < 245)
  {
    weatherMode = 2;
    
    sampleB = constrain (sampleB, 160, 245);
    weatherValue = map (sampleB, 160, 245, 100, 30);
    weatherClouds = map (sampleB, 160, 245, 100, 60);
    return;
  }
}

////////////////////////////////////////////////
//////    MODIFY WEATHER DATA FUNCTION    //////
////////////////////////////////////////////////
// loads the XML file from OpenWeatherMap and changes the precipitation & clouds elements to the new variables

void modifyWeatherData (boolean isImgValid, int mode, float value, float clouds)
{
  // Load XML file from OpenWeatherMap
  xmlWeatherData = loadXML(xmlUrl);
  
  // if image is black (faulty screenshot) set the date back to january 2016 so it gets rejected
  if (isImgValid == false)
  {
    XML xmlDate = xmlWeatherData.getChild ("lastupdate");
    xmlDate.setString ("value", "2016-01-01T00:00:00");
  }
  
  // set mode of precipitation: no, rain or snow
  XML xmlMode = xmlWeatherData.getChild ("precipitation");
  
  switch (mode)
  {
  case 0:
    xmlMode.setString ("mode", "no");
    break;
  case 1:
    xmlMode.setString ("mode", "rain");
    break;
  case 2:
    xmlMode.setString ("mode", "snow");
    break;
  }
  
  // if it rains or snows
  if (mode > 0)
  {
    // set amount of precipitation
    xmlMode.setString ("value", Float.toString(value)); 
    
    // check if there are enough clouds & if not, set them to calculated value
    XML xmlClouds = xmlWeatherData.getChild ("clouds");
    float currentClouds = xmlClouds.getFloat ("value");
    if (currentClouds < clouds)
    {
      xmlClouds.setFloat ("value", clouds);
    }
  }
  
  // save altered XML file
  saveXML (xmlWeatherData, xmlSave);
}