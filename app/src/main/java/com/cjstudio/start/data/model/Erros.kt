package com.cjstudio.start.data.model

// Falhas de negócio que as telas tratam com mensagem própria; o resto cai
// no erro genérico.
class EmailNaoVerificadoException : Exception("E-mail ainda não confirmado.")
class PerfilNaoEncontradoException : Exception("Esta conta ainda não tem cadastro neste app.")
class ContaBloqueadaException : Exception("Conta bloqueada.")
class AcessoAdminRestritoException : Exception("Acesso restrito a administradores.")
class CpfJaCadastradoException : Exception("Já existe um cadastro com este CPF.")
class EmailJaCadastradoException : Exception("Este e-mail já tem conta.")
