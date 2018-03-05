import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import controlP5.*; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class OS_Radar extends PApplet {

//////////////////////////////////
//////     OS_RADAR v3.1    //////
//////////////////////////////////

/*
Date 2017/07/06

Created by Jasper Janssens for OLYMPIA STADION
a video installation by David Claerbout

This is a Processing v3.3.5 script written in Java
using the controlP5 library for the user interface

-----
Date 2018/02/05
v3.1 release

This version was reworked by Haryo Sukmawanto and reviewed by Jasper Janssens for OLYMPIA STADION
a video installation by David Claerbout

OS_Radar now displays the weatherValues and weatherMode in the GUI. 
It now also has a logger that will record these values for each session 
for up three weeks in a .csv file found in log/
*/

// import controlP5 library for UI


// some general variables
PImage webImg;
XML xmlWeatherData;
public int timer;

// variables for loading, using & saving config.xml
XML xmlConfig;
int positionX, positionY;
String xmlUrl;
String xmlSave;
String imgLoad;
String CurrentweatherMode;

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
boolean isOverridden = false;
float sliderValue = 50;
int timerOverride;;

// variables for logging
// Current* strings are set in logWeatherDataWriteRow()
// Init* Strings are used to set the start time of the log
Table logWeatherData;
String CurrentD, CurrentM, CurrentY, CurrentH, CurrentMin;
String InitD = nf(day(), 2);
String InitM = nf(month(), 2);
String InitY = nf(year(), 4);
String InitH = nf(hour(), 2);
String InitMin = nf(minute(), 2);
String weatherValueStr, weatherModeStr;

//////////////////////////////////
//////    SETUP FUNCTION    //////
//////////////////////////////////
// runs when you start the program

public void setup ()  
{
  // set timer to 5 minute countdown
  timer = 15;
  
  // initialize variable
  isImgValid = true;
  
  // setup canvas width & height
  
  
  fill (50);
  rect (0, 0, width, 100);
 
  
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
        displayReturnedValues ();
        logWeatherDataWriteRow();
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
        displayReturnedValues ();
        logWeatherDataWriteRow();
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
        displayReturnedValues ();
        logWeatherDataWriteRow();
      }
    }
  });
  
  // if numberbox TIMER OVERRIDE is changed and toggle ON/OFF is activated, set timer to new value
  n1.addCallback(new CallbackListener() 
  {
    public void controlEvent(CallbackEvent theEvent) 
    {
      if ((theEvent.getAction()==ControlP5.ACTION_RELEASE || theEvent.getAction()==ControlP5.ACTION_RELEASE_OUTSIDE)) 
      {
        timer = timerOverride;
      }
    }
  });
  
  ///////////////////////////////////////
  //////   RUN SCRIPT FIRST TIME   //////
  ///////////////////////////////////////
  
  loadConfig ();
  logWeatherDataSetup();
  runScript ();
}

/////////////////////////////
//////   LOAD CONFIG   //////
/////////////////////////////

public void loadConfig ()
{
  xmlConfig = loadXML ("config.xml");
  
  positionX = xmlConfig.getChild("imgPosition").getInt("x");
  positionY = xmlConfig.getChild("imgPosition").getInt("y");
  xmlUrl = xmlConfig.getChild("xmlWeatherData").getString("url");
  xmlSave = xmlConfig.getChild("xmlSaveLocation").getString("path"); // C:/Users/Administrator/Dropbox/Weatherdata/
  imgLoad = xmlConfig.getChild("imgLoad").getString("path");
}

/////////////////////////////////////
//////   RUN SCRIPT FUNCTION   //////
/////////////////////////////////////

public void runScript ()
{
  sampleImage ();

  if (webImg != null)
  {
    analyzePixel ();
    modifyWeatherData (isImgValid, weatherMode, weatherValue, weatherClouds);
  }
  else
  {
    modifyWeatherData (false, 0, 0, 0);
  }
  
  // run displayReturnedValues here after sampleImage is called and values are set
  displayReturnedValues ();
  
  // Log the weather data
  logWeatherDataWriteRow();
}

////////////////////////////////////////////////
//////    UI WEATHER OVERRIDE FUNCTION    //////
////////////////////////////////////////////////
// converts the variables set in the UI to variables used to modify the weather data XML

public void weatherOverride ()
{
  // Set isOverridden boolean value
  isOverridden = true;
  
  if (rain_snow == true)
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

public void draw ()
{
  // run TIMER FUNCTION
  setTimer ();
  
  // when timer runs out & weather override is OFF, reset timer & RUN SCRIPT FUNCTION
  if (timer <= 0 && on_off == false)
  {
    timer = 15;
    n1.setValue (timer);
    
    // Set isOverridden boolean value
    isOverridden = false;
    
    runScript ();
  }
}

//////////////////////////////////
//////    TIMER FUNCTION    //////
//////////////////////////////////

public void setTimer ()
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
// the hue is also sampled in 8bit, but it is translated from a color wheel value (360\u00b0) (angle = 8bitvalue/255*360)

public void sampleImage ()
{
  try
  {
    webImg = loadImage (imgLoad, "png");
  }
  catch (Exception e)
  {
    print ("Image could not be loaded");
  }
  
  if (webImg != null)
  {
    image (webImg, -(positionX - 200), -(positionY - 220));
    int samplePixel = get (200 , 220);

    //fill (0);
    //rect (0, 0, width, height);
    
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

public void analyzePixel ()
{
  // black screenshot, so set XML date way back
  if (sampleB < 25)
  {
    isImgValid = false;
    return;
  }
  
  // no precipitation
  if (sampleS < 50)
  {
     weatherMode = 0;
     weatherValue = 0;
     return;
  }
   
  // rain
  if (sampleH <= 120)
  {
     weatherMode = 1;
     
     if (sampleH >= 80)
     {
       sampleB = constrain (sampleB, 114, 205);
       weatherValue = map (sampleB, 114, 205, 50, 30);
       weatherClouds = map (sampleB, 114, 205, 75, 60);
       return;
     }
     
     if (sampleH < 80)
     {
       sampleH = constrain (sampleH, 0, 60);
       weatherValue = map (sampleH, 0, 60, 70, 51);
       weatherClouds = map (sampleH, 0, 60, 90, 76);
       return;
     }
  }
  
  if (sampleH >= 200)
  {
    weatherMode = 1;
    
    weatherValue = 70;
    weatherClouds = 90;
    return;
  }

  // snow
  if (sampleH > 120 && sampleH < 200)
  {
    weatherMode = 2;
    
    sampleB = constrain (sampleB, 80, 245);
    weatherValue = map (sampleB, 80, 245, 100, 30);
    weatherClouds = map (sampleB, 80, 245, 100, 60);
    return;
  }
}

////////////////////////////////////////////////
//////    MODIFY WEATHER DATA FUNCTION    //////
////////////////////////////////////////////////
// loads the XML file from OpenWeatherMap and changes the precipitation & clouds elements to the new variables

public void modifyWeatherData (boolean isImgValid, int mode, float value, float clouds)
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



//////////////////////////////////////////
/////     AUXILIARY FUNCTIONS        /////
//////////////////////////////////////////

// This function displays weatherValue and weatherMode in the GUI
public void displayReturnedValues() 
{
  fill (50);
  noStroke();
  rect(160, 50, 160, 40);
  
  fill(255);
  
  //Convert weatherValue to a string variable and displays it
  weatherValueStr = nf(weatherValue);
  text(weatherValueStr, 160, 60, 30, 20);
  text("weatherValue", 160, 85);
  
  //Checks weatherMode and displays Snow, Rain or None depending on the conditions
  if (weatherMode == 1)
  { 
    weatherModeStr = "Rain";
  }
  else if (weatherMode == 2)
  {
    weatherModeStr = "Snow";
  }
  else
  {
    weatherModeStr = "None";
  }
  
  text(weatherModeStr, 240, 60, 40, 20);
  text("weatherMode", 240, 85);
}


// Adds a logger to the program to keep track of changes to Olympia
// Setup logWeatherData as a new Table
public void logWeatherDataSetup()
{
  logWeatherData = new Table();
  
  // Add column headers to logWeatherData
  logWeatherData.addColumn("Date");
  logWeatherData.addColumn("Time");
  logWeatherData.addColumn("WeatherOverride");
  logWeatherData.addColumn("WeatherMode");
  logWeatherData.addColumn("WeatherValue");
  logWeatherData.addColumn("Clouds"); 
  logWeatherData.addColumn("Temperature");
  logWeatherData.addColumn("WindDirection"); 
  logWeatherData.addColumn("WindSpeed"); 
}

// Function that will write the values to the log
public void logWeatherDataWriteRow()
{ 
  //Define date and time the moment the log entry is written
  CurrentD = nf(day(), 2);
  CurrentM = nf(month(), 2);
  CurrentY = nf(year(), 4);
  CurrentH = nf(hour(), 2);
  CurrentMin = nf(minute(), 2);
  
  String cloudsStr = xmlWeatherData.getChild ("clouds").getString("value");
  String tempValueStr = xmlWeatherData.getChild ("temperature").getString("value");
  String windCodeStr = xmlWeatherData.getChild ("wind").getChild("direction").getString("code");
  String windValueStr = xmlWeatherData.getChild ("wind").getChild("speed").getString("value");
  
  // Check length of table, if it exceeds an amount then delete the first row
  // and write new row after
  if (logWeatherData.getRowCount() >= 2000)
  {
    logWeatherData.removeRow(0);
    TableRow newRow = logWeatherData.addRow();
    
    newRow.setString("Date", CurrentY + "/" + CurrentM + "/" + CurrentD);
    newRow.setString("Time", CurrentH + ":" + CurrentMin);
    newRow.setString("WeatherOverride", str(isOverridden));
    newRow.setString("WeatherMode", weatherModeStr);
    newRow.setString("WeatherValue", weatherValueStr);
    newRow.setString("Clouds", cloudsStr); 
    newRow.setString("Temperature", tempValueStr);
    newRow.setString("WindSpeed", windValueStr);
    newRow.setString("WindDirection", windCodeStr);
  }
  else
  { 
    TableRow newRow = logWeatherData.addRow();
    newRow.setString("Date", CurrentY + "/" + CurrentM + "/" + CurrentD);
    newRow.setString("Time", CurrentH + ":" + CurrentMin);
    newRow.setString("WeatherOverride", str(isOverridden));
    newRow.setString("WeatherMode", weatherModeStr);
    newRow.setString("WeatherValue", weatherValueStr);
    newRow.setString("Clouds", cloudsStr); 
    newRow.setString("Temperature", tempValueStr);
    newRow.setString("WindSpeed", windValueStr);
    newRow.setString("WindDirection", windCodeStr);
  }
   
  //Takes the Init* date and time variables as the logfile name
  saveTable(logWeatherData, "log/" + InitY + InitM + InitD + "-" + InitH + "h" + InitMin + ".csv");
}
  public void settings() {  size (400,400); }
  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "OS_Radar" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
