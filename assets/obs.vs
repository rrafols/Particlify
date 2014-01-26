uniform mat4 u_MVPMatrix;

attribute vec4 a_Position;
varying vec4 pos;

void main()
{
	gl_Position = u_MVPMatrix * a_Position;
	pos = a_Position;
}
