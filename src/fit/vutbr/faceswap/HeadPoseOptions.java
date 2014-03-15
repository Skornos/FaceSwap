/**
* Copyright (C) 2013 Imran Akthar (www.imranakthar.com)
* imran@imranakthar.com
*/

package fit.vutbr.faceswap;



public class HeadPoseOptions 
{

public String haarCascadeSrc; //path for haarcascadefile
public int focalLength;

public HeadPoseOptions()
{
	focalLength=600;
	haarCascadeSrc="haarcascade_frontalface_alt2.xml";
}
}
