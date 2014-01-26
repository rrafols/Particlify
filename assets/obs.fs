precision mediump float; 
#extension GL_OES_standard_derivatives : enable                                

varying vec4 pos;
                          
void main()
{
	vec3 pos2 = mod(gl_FragCoord.xyz,vec3(40.0));
	if(((pos2.x > 10.0) && (pos2.y > 10.0) && (pos.z > 5.0)) || ((pos2.x < 10.0) && (pos2.y < 10.0) && (pos.z < 5.0))) {
		//gl_FragColor=vec4(0.2, 0.5, 0.8, 0.8);
		gl_FragColor=vec4(0.7, 0.6, 0.1, 1.0);
	} else {
		//gl_FragColor=vec4(0.4, 0.7, 1.0, 0.8);
		gl_FragColor=vec4(0.0, 0.0, 0.0, 1.0);
	}
}
