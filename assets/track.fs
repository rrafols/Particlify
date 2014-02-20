precision mediump float; 
#extension GL_OES_standard_derivatives : enable                                

flat varying vec4 v_Color; 
varying vec4 pos;
                          
void main()
{
	gl_FragColor = v_Color;
}
