precision mediump float; 
#extension GL_OES_standard_derivatives : enable                                

flat varying vec4 v_Color; 
varying vec4 pos;
                          
void main()
{
	if(pos.x > 55.0) gl_FragColor = vec4(0,0,0,1);
	else if(pos.x < -55.0) gl_FragColor = vec4(0,0,0,1);
	else {
		vec3 pos = mod(pos.xyz,vec3(20.0));
		if(((pos.x > 10.0) && (pos.z > 10.0)) || ((pos.x < 10.0)&&(pos.z < 10.0))) {
			gl_FragColor=vec4(0.2, 0.5, 0.8, 1.0);
		} else {
			gl_FragColor=vec4(0.4, 0.7, 1.0, 1.0);
		}
	}
}
