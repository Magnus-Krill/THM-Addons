/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







// Standalone native-GL preview harness for THM main-menu shaders (.fsh).
// Renders the shader completely unmodified through a real desktop GL 3.3 core
// context with a ThmShaderData UBO (time/mouse/resolution), matching the
// contract in docs/main-menu-shader-spec.md. Interactive by default; pass
// --shot out.ppm to render a single headless frame instead.
#include <GL/glew.h>
#include <GLFW/glfw3.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static char *readFile(const char *path) {
    FILE *f = fopen(path, "rb");
    if (!f) { fprintf(stderr, "cannot open %s\n", path); exit(1); }
    fseek(f, 0, SEEK_END);
    long len = ftell(f);
    fseek(f, 0, SEEK_SET);
    char *buf = malloc(len + 1);
    fread(buf, 1, len, f);
    buf[len] = 0;
    fclose(f);
    return buf;
}

static GLuint compile(GLenum type, const char *src, const char *label) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, NULL);
    glCompileShader(s);
    GLint ok;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[4096];
        glGetShaderInfoLog(s, sizeof(log), NULL, log);
        fprintf(stderr, "%s compile error:\n%s\n", label, log);
        exit(1);
    }
    return s;
}

static const char *VERT_SRC =
    "#version 330 core\n"
    "void main() {\n"
    "    vec2 pos[3] = vec2[3](vec2(-1.0,-1.0), vec2(3.0,-1.0), vec2(-1.0,3.0));\n"
    "    gl_Position = vec4(pos[gl_VertexID], 0.0, 1.0);\n"
    "}\n";

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "usage: %s <shader.fsh> [--shot out.ppm] [--time T] [--width W] [--height H]\n", argv[0]);
        return 1;
    }
    const char *fshPath = argv[1];
    const char *shotPath = NULL;
    float shotTime = 6.0f;
    int benchFrames = 0;
    int width = 1280, height = 720;

    for (int i = 2; i < argc; i++) {
        if (!strcmp(argv[i], "--shot") && i + 1 < argc) shotPath = argv[++i];
        else if (!strcmp(argv[i], "--time") && i + 1 < argc) shotTime = atof(argv[++i]);
        else if (!strcmp(argv[i], "--width") && i + 1 < argc) width = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--height") && i + 1 < argc) height = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--bench") && i + 1 < argc) benchFrames = atoi(argv[++i]);
    }

    // Force X11: GLEW's extension-string probing needs GLX, which isn't
    // present when GLFW picks the native Wayland backend.
    glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
    if (!glfwInit()) { fprintf(stderr, "glfwInit failed\n"); return 1; }
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
    glfwWindowHint(GLFW_VISIBLE, (shotPath || benchFrames) ? GLFW_FALSE : GLFW_TRUE);

    GLFWwindow *win = glfwCreateWindow(width, height, "THM shader preview", NULL, NULL);
    if (!win) { fprintf(stderr, "glfwCreateWindow failed\n"); glfwTerminate(); return 1; }
    glfwMakeContextCurrent(win);

    glewExperimental = GL_TRUE;
    GLenum glewErr = glewInit();
    if (glewErr != GLEW_OK) {
        fprintf(stderr, "glewInit failed: %s\n", glewGetErrorString(glewErr));
        return 1;
    }
    glGetError(); // glewInit() with GLEW_EXPERIMENTAL commonly leaves a spurious GL_INVALID_ENUM

    char *fragSrc = readFile(fshPath);
    GLuint vs = compile(GL_VERTEX_SHADER, VERT_SRC, "vertex");
    GLuint fs = compile(GL_FRAGMENT_SHADER, fragSrc, fshPath);
    GLuint prog = glCreateProgram();
    glAttachShader(prog, vs);
    glAttachShader(prog, fs);
    glLinkProgram(prog);
    GLint linked;
    glGetProgramiv(prog, GL_LINK_STATUS, &linked);
    if (!linked) {
        char log[4096];
        glGetProgramInfoLog(prog, sizeof(log), NULL, log);
        fprintf(stderr, "link error:\n%s\n", log);
        return 1;
    }

    GLuint vao;
    glGenVertexArrays(1, &vao);

    GLuint ubo;
    glGenBuffers(1, &ubo);
    glBindBuffer(GL_UNIFORM_BUFFER, ubo);
    glBufferData(GL_UNIFORM_BUFFER, 32, NULL, GL_DYNAMIC_DRAW);
    GLuint blockIdx = glGetUniformBlockIndex(prog, "ThmShaderData");
    if (blockIdx != GL_INVALID_INDEX) {
        glUniformBlockBinding(prog, blockIdx, 0);
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, ubo);
    } else {
        fprintf(stderr, "warning: ThmShaderData block not found (typo in header?)\n");
    }

    glUseProgram(prog);
    glBindVertexArray(vao);

    // --bench N: time N draws in one process and report ms/frame. Timing whole process
    // invocations instead is useless for comparing shaders - context creation, compilation and
    // the pixel readback swamp the draw, and that overhead is what you end up measuring.
    if (benchFrames > 0) {
        int fbw, fbh;
        glfwGetFramebufferSize(win, &fbw, &fbh);
        glViewport(0, 0, fbw, fbh);
        glBindBuffer(GL_UNIFORM_BUFFER, ubo);

        // The first draws pay for shader/pipeline setup the steady state doesn't, so warm up.
        for (int i = 0; i < 3; i++) {
            float buf[8] = { (float)i, 0.0f, 0.0f, 0.0f, (float)fbw, (float)fbh, 0.0f, 0.0f };
            glBufferSubData(GL_UNIFORM_BUFFER, 0, sizeof(buf), buf);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        }
        glFinish();

        double t0 = glfwGetTime();
        for (int i = 0; i < benchFrames; i++) {
            // Walk time forward so nothing can be cached across frames.
            float buf[8] = { shotTime + i * 0.05f, 0.0f, 0.0f, 0.0f, (float)fbw, (float)fbh, 0.0f, 0.0f };
            glBufferSubData(GL_UNIFORM_BUFFER, 0, sizeof(buf), buf);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        }
        glFinish();

        printf("%.2f ms/frame  %dx%d  (%d frames)\n",
               (glfwGetTime() - t0) * 1000.0 / benchFrames, fbw, fbh, benchFrames);
        glfwTerminate();
        return 0;
    }

    do {
        int fbw, fbh;
        glfwGetFramebufferSize(win, &fbw, &fbh);
        glViewport(0, 0, fbw, fbh);

        double mx = 0, my = 0;
        glfwGetCursorPos(win, &mx, &my);

        float t = shotPath ? shotTime : (float)glfwGetTime();
        float buf[8] = { t, 0.0f, (float)mx, (float)my, (float)fbw, (float)fbh, 0.0f, 0.0f };
        glBindBuffer(GL_UNIFORM_BUFFER, ubo);
        glBufferSubData(GL_UNIFORM_BUFFER, 0, sizeof(buf), buf);

        glDrawArrays(GL_TRIANGLES, 0, 3);

        if (shotPath) {
            unsigned char *pixels = malloc(fbw * fbh * 3);
            glReadPixels(0, 0, fbw, fbh, GL_RGB, GL_UNSIGNED_BYTE, pixels);
            FILE *out = fopen(shotPath, "wb");
            fprintf(out, "P6\n%d %d\n255\n", fbw, fbh);
            for (int y = fbh - 1; y >= 0; y--) {
                fwrite(pixels + y * fbw * 3, 1, fbw * 3, out);
            }
            fclose(out);
            free(pixels);
            printf("wrote %s\n", shotPath);
            break;
        }

        glfwSwapBuffers(win);
        glfwPollEvents();
    } while (!shotPath && !glfwWindowShouldClose(win) && glfwGetKey(win, GLFW_KEY_ESCAPE) != GLFW_PRESS);

    glfwTerminate();
    return 0;
}
