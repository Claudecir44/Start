package com.cjstudio.start.data.model

// Falhas de negócio que as telas tratam com mensagem própria; o resto cai
// no erro genérico.
// reenviado = um link novo acabou de ser enviado (o anterior deixou de valer).
class EmailNaoVerificadoException(val reenviado: Boolean = false) : Exception("E-mail ainda não confirmado.")
class PerfilNaoEncontradoException : Exception("Esta conta ainda não tem cadastro neste app.")
class ContaBloqueadaException : Exception("Conta bloqueada.")
class AcessoAdminRestritoException : Exception("Acesso restrito a administradores.")
class CpfJaCadastradoException : Exception("Já existe um cadastro com este CPF.")
class EmailJaCadastradoException : Exception("Este e-mail já tem conta.")
class PerfilJaExisteException : Exception("Esta conta já tem este cadastro.")
