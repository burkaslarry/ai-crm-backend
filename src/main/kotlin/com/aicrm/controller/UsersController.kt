package com.aicrm.controller

import com.aicrm.domain.User
import com.aicrm.domain.sanitizeString
import com.aicrm.repository.UserRepository
import com.aicrm.util.uuid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RequestMapping("/api/users")
@RestController
class UsersController(private val userRepository: UserRepository) {

    @GetMapping
    fun list(): List<Map<String, Any?>> =
        userRepository.findAll().map { toMap(it) }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<Any> {
        val user = userRepository.findById(id) ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "User not found"))
        return ResponseEntity.ok(toMap(user))
    }

    @PostMapping
    fun create(@RequestBody body: Map<String, Any?>): ResponseEntity<Any> {
        val email = sanitizeString(body["email"]?.toString(), 255) ?: return ResponseEntity.badRequest().body(mapOf("error" to "email required"))
        if (userRepository.findByEmail(email) != null) return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "Email already exists"))
        val name = sanitizeString(body["name"]?.toString(), 255)
        val role = when (val r = sanitizeString(body["role"]?.toString(), 20)?.lowercase()) {
            "superadmin", "operator" -> r
            else -> "operator"
        }
        val user = User(id = uuid(), email = email, name = name, role = role, createdAt = Instant.now())
        userRepository.insert(user)
        return ResponseEntity.status(HttpStatus.CREATED).body(toMap(user))
    }

    @PatchMapping("/{id}")
    fun patch(@PathVariable id: String, @RequestBody body: Map<String, Any?>): ResponseEntity<Any> {
        val user = userRepository.findById(id) ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "User not found"))
        val role = body["role"]?.toString()?.lowercase()?.take(20)
        if (role != null && (role == "superadmin" || role == "operator")) {
            userRepository.updateRole(id, role)
            val updated = userRepository.findById(id)!!
            return ResponseEntity.ok(toMap(updated))
        }
        return ResponseEntity.ok(toMap(user))
    }

    private fun toMap(u: User) = mapOf(
        "id" to u.id,
        "email" to u.email,
        "name" to u.name,
        "role" to u.role,
        "created_at" to u.createdAt.toString()
    )
}
